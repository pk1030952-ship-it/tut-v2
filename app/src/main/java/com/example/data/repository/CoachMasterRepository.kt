package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.util.FeeCalculator
import com.example.util.SecurityUtils
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class CoachMasterRepository(
    private val database: AppDatabase,
    private val appContext: Context? = null
) {

    companion object {
        const val COLLECTION_STUDENTS = "students"
        const val COLLECTION_ATTENDANCE = "attendance"
        const val COLLECTION_PAYMENTS = "payments"
        const val COLLECTION_BATCHES = "batches"
        const val COLLECTION_STAFF = "staff"
        const val COLLECTION_EXPENSES = "expenses"
        const val COLLECTION_EXAMS = "exams"
        const val COLLECTION_EXAM_RESULTS = "examResults"
        const val COLLECTION_COMMUNICATIONS = "communications"
        const val COLLECTION_SETTINGS = "settings"
        const val DOC_INSTITUTE_CONFIG = "instituteConfig"

        // Legacy single-document path (used only for one-time automatic migration if collections are empty)
        private const val LEGACY_COLLECTION = "tuitionAppDb"
        private const val LEGACY_DOC = "mainData"

        // Max operations per Firestore WriteBatch (limit is 500; 400 keeps safe margin for lakhs of records)
        private const val BATCH_CHUNK_SIZE = 400

        @Volatile
        private var INSTANCE: CoachMasterRepository? = null

        fun getInstance(context: Context): CoachMasterRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = CoachMasterRepository(
                    database = AppDatabase.getInstance(context.applicationContext),
                    appContext = context.applicationContext
                )
                INSTANCE = instance
                instance
            }
        }
    }

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val studentDao = database.studentDao()
    private val batchDao = database.batchDao()
    private val staffDao = database.staffDao()
    private val attendanceDao = database.attendanceDao()
    private val paymentDao = database.paymentDao()
    private val expenseDao = database.expenseDao()
    private val examDao = database.examDao()
    private val commDao = database.communicationDao()
    private val configDao = database.instituteConfigDao()

    val allStudents: Flow<List<Student>> = studentDao.getAllStudents()
    val activeStudents: Flow<List<Student>> = studentDao.getActiveStudents()
    val allBatches: Flow<List<Batch>> = batchDao.getAllBatches()
    val allStaff: Flow<List<Staff>> = staffDao.getAllStaff()
    val allAttendance: Flow<List<AttendanceRecord>> = attendanceDao.getAllAttendance()
    val allPayments: Flow<List<PaymentRecord>> = paymentDao.getAllPayments()
    val allExpenses: Flow<List<ExpenseRecord>> = expenseDao.getAllExpenses()
    val allExams: Flow<List<Exam>> = examDao.getAllExams()
    val allExamResults: Flow<List<ExamResult>> = examDao.getAllExamResults()
    val allCommunications: Flow<List<CommunicationLog>> = commDao.getAllLogs()
    val config: Flow<InstituteConfig?> = configDao.getConfig()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isCloudConnected = MutableStateFlow(true)
    val isCloudConnected: StateFlow<Boolean> = _isCloudConnected.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    @Volatile
    private var isApplyingRemoteUpdate = false

    @Volatile
    private var isInitialized = false
    private val snapshotListeners = mutableListOf<ListenerRegistration>()

    fun postStatusMessage(msg: String?) {
        _statusMessage.value = msg
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun ensureInitialized() {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            isInitialized = true
            repoScope.launch {
                _isSyncing.value = true
                val result = pullFromCloud()
                _isSyncing.value = false
                _isCloudConnected.value = result.isSuccess
                startRealtimeCloudSync { connected, msg ->
                    _isCloudConnected.value = connected
                    if (msg != null) _statusMessage.value = msg
                }
            }
        }
    }

    private fun getIsoNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun parseIsoToMillis(iso: String?): Long {
        if (iso.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(iso.substringBefore("."))?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            try {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)?.time ?: System.currentTimeMillis()
            } catch (ex: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    private fun getFirestoreSafely(): FirebaseFirestore? {
        return try {
            val existingApps = FirebaseApp.getApps(appContext ?: return FirebaseFirestore.getInstance())
            if (existingApps.isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey("AIzaSyDJXpAReUe40BZCT8IFblzxeD5MxzGuR2c")
                    .setApplicationId("1:314227009599:web:ef05d1c5560841718b308a")
                    .setProjectId("tution-pro-32a33")
                    .setStorageBucket("tution-pro-32a33.firebasestorage.app")
                    .setGcmSenderId("314227009599")
                    .build()
                FirebaseApp.initializeApp(appContext, options)
            }
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            try {
                FirebaseFirestore.getInstance()
            } catch (ex: Exception) {
                null
            }
        }
    }

    /**
     * Starts real-time Firestore collection listeners on:
     * - `students/{studentId}`
     * - `attendance/{date}`
     * - `payments/{paymentId}`
     * Eliminates the 1MB single-document limit while keeping multi-device real-time sync active.
     */
    fun startRealtimeCloudSync(onStatusChange: (Boolean, String?) -> Unit = { _, _ -> }) {
        val firestore = getFirestoreSafely() ?: return
        if (snapshotListeners.isNotEmpty()) return

        // 1. Real-time listener on `students/{studentId}`
        val studentsListener = firestore.collection(COLLECTION_STUDENTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onStatusChange(false, null)
                    return@addSnapshotListener
                }
                if (snapshot != null && !snapshot.isEmpty && !isApplyingRemoteUpdate) {
                    repoScope.launch {
                        isApplyingRemoteUpdate = true
                        try {
                            val parsed = snapshot.documents.mapNotNull { doc ->
                                parseStudentMap(doc.id, doc.data ?: emptyMap())
                            }
                            if (parsed.isNotEmpty()) {
                                studentDao.insertStudents(parsed)
                            }
                            onStatusChange(true, null)
                        } finally {
                            isApplyingRemoteUpdate = false
                        }
                    }
                }
            }

        // 2. Real-time listener on `attendance/{date}`
        val attendanceListener = firestore.collection(COLLECTION_ATTENDANCE)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onStatusChange(false, null)
                    return@addSnapshotListener
                }
                if (snapshot != null && !snapshot.isEmpty && !isApplyingRemoteUpdate) {
                    repoScope.launch {
                        isApplyingRemoteUpdate = true
                        try {
                            val studentBatchMap = studentDao.getAllStudents().first().associate { it.id to it.batchId }
                            val records = mutableListOf<AttendanceRecord>()
                            snapshot.documents.forEach { doc ->
                                val dateStr = doc.id
                                val data = doc.data ?: emptyMap()
                                records.addAll(parseAttendanceDateDoc(dateStr, data, studentBatchMap))
                            }
                            if (records.isNotEmpty()) {
                                attendanceDao.insertAttendanceList(records)
                            }
                            onStatusChange(true, null)
                        } finally {
                            isApplyingRemoteUpdate = false
                        }
                    }
                }
            }

        // 3. Real-time listener on `payments/{paymentId}`
        val paymentsListener = firestore.collection(COLLECTION_PAYMENTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onStatusChange(false, null)
                    return@addSnapshotListener
                }
                if (snapshot != null && !snapshot.isEmpty && !isApplyingRemoteUpdate) {
                    repoScope.launch {
                        isApplyingRemoteUpdate = true
                        try {
                            val parsed = snapshot.documents.mapNotNull { doc ->
                                parsePaymentMap(doc.id, doc.data ?: emptyMap())
                            }
                            if (parsed.isNotEmpty()) {
                                paymentDao.insertPayments(parsed)
                            }
                            onStatusChange(true, null)
                        } finally {
                            isApplyingRemoteUpdate = false
                        }
                    }
                }
            }

        snapshotListeners.add(studentsListener)
        snapshotListeners.add(attendanceListener)
        snapshotListeners.add(paymentsListener)
    }

    fun getAttendanceForDate(date: String): Flow<List<AttendanceRecord>> =
        attendanceDao.getAttendanceForDate(date)

    fun getAttendanceForStudent(studentId: String): Flow<List<AttendanceRecord>> =
        attendanceDao.getAttendanceForStudent(studentId)

    fun getPaymentsForStudent(studentId: String): Flow<List<PaymentRecord>> =
        paymentDao.getPaymentsForStudent(studentId)

    fun getExamResults(examId: String): Flow<List<ExamResult>> =
        examDao.getResultsForExam(examId)

    fun getCommunicationLogs(studentId: String): Flow<List<CommunicationLog>> =
        commDao.getLogsForStudent(studentId)

    private fun studentToFirestoreMap(s: Student): Map<String, Any?> = mapOf(
        "id" to s.id,
        "roll" to s.rollNo,
        "rollNo" to s.rollNo,
        "name" to s.name,
        "phone" to s.phone,
        "parentPhone" to s.parentPhone,
        "batchId" to s.batchId,
        "monthlyFee" to s.monthlyFee,
        "joiningDate" to s.joiningDate,
        "status" to s.status,
        "notes" to s.notes,
        "updatedAt" to getIsoNow()
    )

    private fun paymentToFirestoreMap(p: PaymentRecord): Map<String, Any?> {
        val iso = p.isoTimestamp.ifBlank { getIsoNow() }
        return mapOf(
            "id" to p.id,
            "transactionId" to p.id,
            "studentId" to p.studentId,
            "amount" to p.amount,
            "amountPaid" to p.amount,
            "discount" to p.discount,
            "paymentDate" to p.paymentDate,
            "mode" to p.paymentMode,
            "paymentMode" to p.paymentMode,
            "remarks" to p.remarks,
            "timestamp" to iso,
            "timestampMillis" to p.timestamp
        )
    }

    private fun batchToFirestoreMap(b: Batch): Map<String, Any?> = mapOf(
        "id" to b.id,
        "name" to b.name,
        "subject" to b.subject,
        "schedule" to b.schedule,
        "timing" to b.timing,
        "teacherId" to b.teacherId,
        "teacherName" to b.teacherName,
        "classesPerCycle" to b.classesPerCycle
    )

    private fun staffToFirestoreMap(st: Staff): Map<String, Any?> = mapOf(
        "id" to st.id,
        "name" to st.name,
        "role" to st.role,
        "subject" to st.subject,
        "phone" to st.phone,
        "salary" to st.salary
    )

    private fun expenseToFirestoreMap(e: ExpenseRecord): Map<String, Any?> = mapOf(
        "id" to e.id,
        "date" to e.date,
        "category" to e.category,
        "description" to e.description,
        "amount" to e.amount
    )

    private fun examToFirestoreMap(ex: Exam): Map<String, Any?> = mapOf(
        "id" to ex.id,
        "name" to ex.name,
        "batchId" to ex.batchId,
        "date" to ex.date,
        "totalMarks" to ex.totalMarks
    )

    private fun examResultToFirestoreMap(r: ExamResult): Map<String, Any?> = mapOf(
        "id" to r.id,
        "examId" to r.examId,
        "studentId" to r.studentId,
        "marksObtained" to r.marksObtained
    )

    private fun commToFirestoreMap(c: CommunicationLog): Map<String, Any?> = mapOf(
        "id" to c.id,
        "studentId" to c.studentId,
        "date" to c.date,
        "note" to c.note,
        "timestamp" to c.isoTimestamp.ifBlank { getIsoNow() }
    )

    // Student CRUD -> `students/{studentId}`
    suspend fun saveStudent(student: Student) = withContext(Dispatchers.IO) {
        studentDao.insertStudent(student)
        syncDocumentSafely { firestore ->
            firestore.collection(COLLECTION_STUDENTS)
                .document(student.id)
                .set(studentToFirestoreMap(student))
                .await()
        }
    }

    suspend fun deleteStudent(studentId: String) = withContext(Dispatchers.IO) {
        val studentPayments = paymentDao.getPaymentsForStudent(studentId).first()
        val studentComms = commDao.getLogsForStudent(studentId).first()
        val studentAttDates = attendanceDao.getAttendanceForStudent(studentId).first().map { it.date }.distinct()

        studentDao.deleteStudentById(studentId)
        attendanceDao.deleteForStudent(studentId)
        paymentDao.deleteForStudent(studentId)
        commDao.deleteForStudent(studentId)
        examDao.deleteResultsForStudent(studentId)

        syncDocumentSafely { firestore ->
            firestore.collection(COLLECTION_STUDENTS).document(studentId).delete().await()
            studentPayments.forEach { p ->
                firestore.collection(COLLECTION_PAYMENTS).document(p.id).delete().await()
            }
            studentComms.forEach { c ->
                firestore.collection(COLLECTION_COMMUNICATIONS).document(c.id).delete().await()
            }
            studentAttDates.forEach { dateStr ->
                syncAttendanceDateDocument(firestore, dateStr)
            }
        }
    }

    suspend fun toggleStudentStatus(student: Student) = withContext(Dispatchers.IO) {
        val newStatus = if (student.status == "active") "inactive" else "active"
        val updated = student.copy(status = newStatus)
        studentDao.updateStudent(updated)
        syncDocumentSafely { firestore ->
            firestore.collection(COLLECTION_STUDENTS)
                .document(updated.id)
                .set(studentToFirestoreMap(updated))
                .await()
        }
    }

    // Batch CRUD -> `batches/{batchId}`
    suspend fun saveBatch(batch: Batch) = withContext(Dispatchers.IO) {
        batchDao.insertBatch(batch)
        syncDocumentSafely { firestore ->
            firestore.collection(COLLECTION_BATCHES)
                .document(batch.id)
                .set(batchToFirestoreMap(batch))
                .await()
        }
    }

    suspend fun deleteBatch(batch: Batch) = withContext(Dispatchers.IO) {
        batchDao.deleteBatch(batch)
        val enrolled = studentDao.getAllStudents().first().filter { it.batchId == batch.id }
        enrolled.forEach { s ->
            val updated = s.copy(batchId = null)
            studentDao.updateStudent(updated)
        }
        syncDocumentSafely { firestore ->
            firestore.collection(COLLECTION_BATCHES).document(batch.id).delete().await()
            enrolled.forEach { s ->
                firestore.collection(COLLECTION_STUDENTS)
                    .document(s.id)
                    .set(studentToFirestoreMap(s.copy(batchId = null)))
                    .await()
            }
        }
    }

    // Staff CRUD -> `staff/{staffId}`
    suspend fun saveStaff(staff: Staff) = withContext(Dispatchers.IO) {
        staffDao.insertStaff(staff)
        syncDocumentSafely { firestore ->
            firestore.collection(COLLECTION_STAFF)
                .document(staff.id)
                .set(staffToFirestoreMap(staff))
                .await()
        }
    }

    suspend fun deleteStaff(staff: Staff) = withContext(Dispatchers.IO) {
        staffDao.deleteStaff(staff)
        val assignedBatches = batchDao.getAllBatches().first().filter { it.teacherId == staff.id }
        assignedBatches.forEach { b ->
            batchDao.insertBatch(b.copy(teacherId = null, teacherName = ""))
        }
        syncDocumentSafely { firestore ->
            firestore.collection(COLLECTION_STAFF).document(staff.id).delete().await()
            assignedBatches.forEach { b ->
                firestore.collection(COLLECTION_BATCHES)
                    .document(b.id)
                    .set(batchToFirestoreMap(b.copy(teacherId = null, teacherName = "")))
                    .await()
            }
        }
    }

    // Attendance -> `attendance/{date}`
    suspend fun recordAttendanceBatch(date: String, records: List<AttendanceRecord>) = withContext(Dispatchers.IO) {
        attendanceDao.insertAttendanceList(records)
        val affectedDates = (records.map { it.date } + date).distinct()
        syncDocumentSafely { firestore ->
            affectedDates.forEach { dateStr ->
                syncAttendanceDateDocument(firestore, dateStr)
            }
        }
    }

    private suspend fun syncAttendanceDateDocument(firestore: FirebaseFirestore, dateStr: String) {
        val dayRecords = attendanceDao.getAttendanceForDate(dateStr).first()
        val docRef = firestore.collection(COLLECTION_ATTENDANCE).document(dateStr)
        if (dayRecords.isEmpty()) {
            docRef.delete().await()
        } else {
            val mappedRecords = dayRecords.map { r ->
                mapOf(
                    "studentId" to r.studentId,
                    "batchId" to r.batchId,
                    "status" to r.status,
                    "reason" to r.reason
                )
            }
            docRef.set(
                mapOf(
                    "date" to dateStr,
                    "records" to mappedRecords,
                    "updatedAt" to getIsoNow()
                )
            ).await()
        }
    }

    // Payment -> `payments/{paymentId}`
    suspend fun recordPayment(payment: PaymentRecord) = withContext(Dispatchers.IO) {
        val withIso = if (payment.isoTimestamp.isBlank()) {
            payment.copy(isoTimestamp = getIsoNow())
        } else payment
        paymentDao.insertPayment(withIso)
        syncDocumentSafely { firestore ->
            firestore.collection(COLLECTION_PAYMENTS)
                .document(withIso.id)
                .set(paymentToFirestoreMap(withIso))
                .await()
        }
    }

    suspend fun deletePayment(payment: PaymentRecord) = withContext(Dispatchers.IO) {
        paymentDao.deletePayment(payment)
        syncDocumentSafely { firestore ->
            firestore.collection(COLLECTION_PAYMENTS)
                .document(payment.id)
                .delete()
                .await()
        }
    }

    // Expense -> `expenses/{expenseId}`
    suspend fun recordExpense(expense: ExpenseRecord) = withContext(Dispatchers.IO) {
        expenseDao.insertExpense(expense)
        syncDocumentSafely { firestore ->
            firestore.collection(COLLECTION_EXPENSES)
                .document(expense.id)
                .set(expenseToFirestoreMap(expense))
                .await()
        }
    }

    suspend fun deleteExpense(expense: ExpenseRecord) = withContext(Dispatchers.IO) {
        expenseDao.deleteExpense(expense)
        syncDocumentSafely { firestore ->
            firestore.collection(COLLECTION_EXPENSES)
                .document(expense.id)
                .delete()
                .await()
        }
    }

    // Exam & Results -> `exams/{examId}` and `examResults/{resultId}`
    suspend fun saveExam(exam: Exam) = withContext(Dispatchers.IO) {
        examDao.insertExam(exam)
        syncDocumentSafely { firestore ->
            firestore.collection(COLLECTION_EXAMS)
                .document(exam.id)
                .set(examToFirestoreMap(exam))
                .await()
        }
    }

    suspend fun deleteExam(exam: Exam) = withContext(Dispatchers.IO) {
        val existingResults = examDao.getResultsForExam(exam.id).first()
        examDao.deleteResultsForExam(exam.id)
        examDao.deleteExam(exam)
        syncDocumentSafely { firestore ->
            firestore.collection(COLLECTION_EXAMS).document(exam.id).delete().await()
            existingResults.forEach { r ->
                firestore.collection(COLLECTION_EXAM_RESULTS).document(r.id).delete().await()
            }
        }
    }

    suspend fun saveExamResults(examId: String, results: List<ExamResult>) = withContext(Dispatchers.IO) {
        val oldResults = examDao.getResultsForExam(examId).first()
        examDao.deleteResultsForExam(examId)
        examDao.insertResults(results)
        syncDocumentSafely { firestore ->
            oldResults.forEach { old ->
                firestore.collection(COLLECTION_EXAM_RESULTS).document(old.id).delete().await()
            }
            results.forEach { r ->
                firestore.collection(COLLECTION_EXAM_RESULTS)
                    .document(r.id)
                    .set(examResultToFirestoreMap(r))
                    .await()
            }
        }
    }

    // Communication Log -> `communications/{logId}`
    suspend fun addCommunicationLog(log: CommunicationLog) = withContext(Dispatchers.IO) {
        val withIso = if (log.isoTimestamp.isBlank()) {
            log.copy(isoTimestamp = getIsoNow())
        } else log
        commDao.insertLog(withIso)
        syncDocumentSafely { firestore ->
            firestore.collection(COLLECTION_COMMUNICATIONS)
                .document(withIso.id)
                .set(commToFirestoreMap(withIso))
                .await()
        }
    }

    suspend fun deleteCommunicationLog(log: CommunicationLog) = withContext(Dispatchers.IO) {
        commDao.deleteLog(log)
        syncDocumentSafely { firestore ->
            firestore.collection(COLLECTION_COMMUNICATIONS)
                .document(log.id)
                .delete()
                .await()
        }
    }

    // Config -> `settings/instituteConfig` (Always stores hashed PIN & Password in Room)
    suspend fun saveConfig(config: InstituteConfig) = withContext(Dispatchers.IO) {
        val securedConfig = config.copy(
            adminPin = SecurityUtils.hashSecret(config.adminPin),
            adminPassword = SecurityUtils.hashSecret(config.adminPassword)
        )
        configDao.saveConfig(securedConfig)
        syncDocumentSafely { firestore ->
            val settingsMap = mapOf(
                "darkMode" to securedConfig.darkMode,
                "instituteName" to securedConfig.instituteName,
                "address" to securedConfig.address,
                "contactPhone" to securedConfig.contactPhone,
                "defaultClassesPerCycle" to securedConfig.defaultClassesPerCycle,
                "updatedAt" to getIsoNow()
            )
            firestore.collection(COLLECTION_SETTINGS)
                .document(DOC_INSTITUTE_CONFIG)
                .set(settingsMap)
                .await()
        }
    }

    private suspend fun syncDocumentSafely(block: suspend (FirebaseFirestore) -> Unit) {
        val firestore = getFirestoreSafely() ?: return
        isApplyingRemoteUpdate = true
        try {
            block(firestore)
            _isCloudConnected.value = true
        } catch (e: Exception) {
            _isCloudConnected.value = false
        } finally {
            isApplyingRemoteUpdate = false
        }
    }

    // Factory Reset (clears both local Room DB and Firestore Collections)
    suspend fun factoryReset() = withContext(Dispatchers.IO) {
        val currentConfig = configDao.getConfigSync() ?: InstituteConfig()
        database.clearAllTables()
        val resetConfig = currentConfig.copy(
            id = 1,
            instituteName = currentConfig.instituteName.ifBlank { "CoachMaster" },
            address = currentConfig.address,
            adminPin = SecurityUtils.hashSecret(currentConfig.adminPin),
            adminPassword = SecurityUtils.hashSecret(currentConfig.adminPassword)
        )
        configDao.saveConfig(resetConfig)
        clearAllCloudCollections()
        pushToCloud()
    }

    private suspend fun clearAllCloudCollections() {
        val firestore = getFirestoreSafely() ?: return
        val collections = listOf(
            COLLECTION_STUDENTS,
            COLLECTION_ATTENDANCE,
            COLLECTION_PAYMENTS,
            COLLECTION_BATCHES,
            COLLECTION_STAFF,
            COLLECTION_EXPENSES,
            COLLECTION_EXAMS,
            COLLECTION_EXAM_RESULTS,
            COLLECTION_COMMUNICATIONS
        )
        try {
            val refsToDelete = mutableListOf<DocumentReference>()
            for (col in collections) {
                val snap = firestore.collection(col).get().await()
                refsToDelete.addAll(snap.documents.map { it.reference })
            }
            refsToDelete.chunked(BATCH_CHUNK_SIZE).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { ref -> batch.delete(ref) }
                batch.commit().await()
            }
        } catch (_: Exception) {
        }
    }

    // Demo Data Seeder
    suspend fun seedDemoData() = withContext(Dispatchers.IO) {
        val batchId = UUID.randomUUID().toString()
        val batch = Batch(
            id = batchId,
            name = "Science Batch X",
            subject = "Science",
            schedule = "Mon-Wed-Fri",
            timing = "4 PM - 5 PM",
            teacherId = null,
            teacherName = ""
        )
        batchDao.insertBatch(batch)

        val stuId1 = UUID.randomUUID().toString()
        val s1 = Student(
            id = stuId1,
            rollNo = "101",
            name = "Rahul Sharma",
            phone = "9876543210",
            batchId = batchId,
            monthlyFee = 1500.0,
            joiningDate = "2026-07-01",
            status = "active"
        )

        val stuId2 = UUID.randomUUID().toString()
        val s2 = Student(
            id = stuId2,
            rollNo = "102",
            name = "Priya Singh",
            phone = "9876543211",
            batchId = batchId,
            monthlyFee = 1500.0,
            joiningDate = "2026-07-15",
            status = "active"
        )
        studentDao.insertStudents(listOf(s1, s2))

        val payment = PaymentRecord(
            id = UUID.randomUUID().toString(),
            studentId = stuId1,
            amount = 1500.0,
            paymentDate = "2026-07-05",
            paymentMode = "Cash",
            remarks = "July Fee",
            timestamp = System.currentTimeMillis(),
            isoTimestamp = getIsoNow()
        )
        paymentDao.insertPayment(payment)

        val expense = ExpenseRecord(
            id = UUID.randomUUID().toString(),
            date = FeeCalculator.getTodayStr(),
            category = "Rent",
            description = "Hall Rent",
            amount = 5000.0
        )
        expenseDao.insertExpense(expense)

        val demoAttendance = mutableListOf<AttendanceRecord>()
        val cal = java.util.Calendar.getInstance()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        for (i in 35 downTo 1) {
            val dCal = cal.clone() as java.util.Calendar
            dCal.add(java.util.Calendar.DAY_OF_YEAR, -i)
            val dateStr = fmt.format(dCal.time)
            val isHoliday = (i == 10 || i == 20 || i == 30)
            val statusRahul = when {
                isHoliday -> "holiday"
                i % 9 == 0 -> "absent"
                else -> "present"
            }
            val reasonStr = if (isHoliday) "Festival Holiday" else ""
            demoAttendance.add(
                AttendanceRecord(
                    date = dateStr,
                    studentId = stuId1,
                    batchId = batchId,
                    status = statusRahul,
                    reason = reasonStr
                )
            )
            if (i <= 26) {
                val statusPriya = when {
                    isHoliday -> "holiday"
                    i == 5 -> "leave"
                    i % 7 == 0 -> "absent"
                    else -> "present"
                }
                val reasonPriya = when {
                    isHoliday -> "Festival Holiday"
                    i == 5 -> "Medical Leave"
                    else -> ""
                }
                demoAttendance.add(
                    AttendanceRecord(
                        date = dateStr,
                        studentId = stuId2,
                        batchId = batchId,
                        status = statusPriya,
                        reason = reasonPriya
                    )
                )
            }
        }
        attendanceDao.insertAttendanceList(demoAttendance)

        pushToCloud()
    }

    /**
     * Pulls data from Firestore Collections:
     * - `students/{studentId}`
     * - `attendance/{date}`
     * - `payments/{paymentId}`
     * - plus `batches`, `staff`, `expenses`, `exams`, `examResults`, `communications`, `settings`.
     * If the collections are empty on first upgrade, automatically checks legacy `tuitionAppDb/mainData`
     * and migrates it into the new collection structure.
     */
    suspend fun pullFromCloud(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val firestore = getFirestoreSafely()
                ?: return@withContext Result.failure(IllegalStateException("Firebase not available"))

            val studentsSnap = firestore.collection(COLLECTION_STUDENTS).get().await()
            val attendanceSnap = firestore.collection(COLLECTION_ATTENDANCE).get().await()
            val paymentsSnap = firestore.collection(COLLECTION_PAYMENTS).get().await()
            val batchesSnap = firestore.collection(COLLECTION_BATCHES).get().await()
            val staffSnap = firestore.collection(COLLECTION_STAFF).get().await()
            val expensesSnap = firestore.collection(COLLECTION_EXPENSES).get().await()
            val examsSnap = firestore.collection(COLLECTION_EXAMS).get().await()
            val examResultsSnap = firestore.collection(COLLECTION_EXAM_RESULTS).get().await()
            val commsSnap = firestore.collection(COLLECTION_COMMUNICATIONS).get().await()
            val settingsSnap = firestore.collection(COLLECTION_SETTINGS).document(DOC_INSTITUTE_CONFIG).get().await()

            val hasCollectionData = !studentsSnap.isEmpty || !attendanceSnap.isEmpty || !paymentsSnap.isEmpty || !batchesSnap.isEmpty

            if (hasCollectionData) {
                isApplyingRemoteUpdate = true
                try {
                    val parsedStaff = staffSnap.documents.mapNotNull { doc ->
                        parseStaffMap(doc.id, doc.data ?: emptyMap())
                    }
                    val staffById = parsedStaff.associateBy { it.id }

                    val parsedBatches = batchesSnap.documents.mapNotNull { doc ->
                        parseBatchMap(doc.id, doc.data ?: emptyMap(), staffById)
                    }

                    val parsedStudents = studentsSnap.documents.mapNotNull { doc ->
                        parseStudentMap(doc.id, doc.data ?: emptyMap())
                    }
                    val studentBatchMap = parsedStudents.associate { it.id to it.batchId }

                    val parsedAttendance = mutableListOf<AttendanceRecord>()
                    attendanceSnap.documents.forEach { doc ->
                        parsedAttendance.addAll(
                            parseAttendanceDateDoc(doc.id, doc.data ?: emptyMap(), studentBatchMap)
                        )
                    }

                    val parsedPayments = paymentsSnap.documents.mapNotNull { doc ->
                        parsePaymentMap(doc.id, doc.data ?: emptyMap())
                    }

                    val parsedExpenses = expensesSnap.documents.mapNotNull { doc ->
                        parseExpenseMap(doc.id, doc.data ?: emptyMap())
                    }

                    val parsedExams = examsSnap.documents.mapNotNull { doc ->
                        parseExamMap(doc.id, doc.data ?: emptyMap())
                    }

                    val parsedResults = examResultsSnap.documents.mapNotNull { doc ->
                        parseExamResultMap(doc.data ?: emptyMap())
                    }

                    val parsedComms = commsSnap.documents.mapNotNull { doc ->
                        parseCommMap(doc.id, doc.data ?: emptyMap())
                    }

                    val existingConfig = configDao.getConfigSync() ?: InstituteConfig()
                    val updatedConfig = if (settingsSnap.exists() && settingsSnap.data != null) {
                        parseSettingsMap(settingsSnap.data!!, existingConfig)
                    } else existingConfig

                    database.withTransaction {
                        studentDao.deleteAll()
                        batchDao.deleteAll()
                        staffDao.deleteAll()
                        attendanceDao.deleteAll()
                        paymentDao.deleteAll()
                        commDao.deleteAll()
                        examDao.deleteAllExamResults()
                        examDao.deleteAllExams()
                        expenseDao.deleteAll()

                        staffDao.insertStaffList(parsedStaff)
                        batchDao.insertBatches(parsedBatches)
                        studentDao.insertStudents(parsedStudents)
                        attendanceDao.insertAttendanceList(parsedAttendance)
                        paymentDao.insertPayments(parsedPayments)
                        commDao.insertLogs(parsedComms)
                        examDao.insertExams(parsedExams)
                        examDao.insertResults(parsedResults)
                        expenseDao.insertExpenses(parsedExpenses)
                        configDao.saveConfig(updatedConfig)
                    }
                } finally {
                    isApplyingRemoteUpdate = false
                }
                val count = studentDao.getAllStudents().first().size
                Result.success("Synced from Firestore Collections ($count students loaded)")
            } else {
                // Check if legacy single document exists for one-time automatic migration to Collections
                val legacySnap = firestore.collection(LEGACY_COLLECTION).document(LEGACY_DOC).get().await()
                if (legacySnap.exists() && legacySnap.data != null) {
                    applyCloudMapToLocalDb(legacySnap.data!!)
                    pushToCloud()
                    val count = studentDao.getAllStudents().first().size
                    Result.success("Migrated $count students to Firestore Collections architecture")
                } else {
                    pushToCloud()
                    Result.success("Initialized Firestore Collections database")
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Pushes all local Room entities to Firestore Collections:
     * - `students/{studentId}`
     * - `attendance/{date}`
     * - `payments/{paymentId}`
     * - plus `batches/{batchId}`, `staff/{staffId}`, `expenses/{expenseId}`, `exams/{examId}`,
     *   `examResults/{resultId}`, `communications/{logId}`, and `settings/instituteConfig`.
     * Uses chunked WriteBatch commits (400 ops per batch) to scale to lakhs of records without hitting 1MB or 500-op limits.
     */
    suspend fun pushToCloud(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val firestore = getFirestoreSafely()
                ?: return@withContext Result.failure(IllegalStateException("Firebase not available"))
            isApplyingRemoteUpdate = true

            val students = studentDao.getAllStudents().first()
            val batches = batchDao.getAllBatches().first()
            val staffList = staffDao.getAllStaff().first()
            val attendanceList = attendanceDao.getAllAttendance().first()
            val payments = paymentDao.getAllPayments().first()
            val expenses = expenseDao.getAllExpenses().first()
            val exams = examDao.getAllExams().first()
            val examResults = examDao.getAllExamResults().first()
            val comms = commDao.getAllLogs().first()
            val currentConfig = configDao.getConfigSync() ?: InstituteConfig()

            val operations = mutableListOf<Pair<DocumentReference, Map<String, Any?>>>()

            // 1. `students/{studentId}`
            students.forEach { s ->
                operations.add(
                    firestore.collection(COLLECTION_STUDENTS).document(s.id) to studentToFirestoreMap(s)
                )
            }

            // 2. `attendance/{date}`
            attendanceList.groupBy { it.date }.forEach { (dateStr, records) ->
                val dayItems = records.map { r ->
                    mapOf(
                        "studentId" to r.studentId,
                        "batchId" to r.batchId,
                        "status" to r.status,
                        "reason" to r.reason
                    )
                }
                operations.add(
                    firestore.collection(COLLECTION_ATTENDANCE).document(dateStr) to mapOf(
                        "date" to dateStr,
                        "records" to dayItems,
                        "updatedAt" to getIsoNow()
                    )
                )
            }

            // 3. `payments/{paymentId}`
            payments.forEach { p ->
                operations.add(
                    firestore.collection(COLLECTION_PAYMENTS).document(p.id) to paymentToFirestoreMap(p)
                )
            }

            // 4. Supporting collections (`batches`, `staff`, `expenses`, `exams`, `examResults`, `communications`, `settings`)
            batches.forEach { b ->
                operations.add(
                    firestore.collection(COLLECTION_BATCHES).document(b.id) to batchToFirestoreMap(b)
                )
            }
            staffList.forEach { st ->
                operations.add(
                    firestore.collection(COLLECTION_STAFF).document(st.id) to staffToFirestoreMap(st)
                )
            }
            expenses.forEach { e ->
                operations.add(
                    firestore.collection(COLLECTION_EXPENSES).document(e.id) to expenseToFirestoreMap(e)
                )
            }
            exams.forEach { ex ->
                operations.add(
                    firestore.collection(COLLECTION_EXAMS).document(ex.id) to examToFirestoreMap(ex)
                )
            }
            examResults.forEach { r ->
                operations.add(
                    firestore.collection(COLLECTION_EXAM_RESULTS).document(r.id) to examResultToFirestoreMap(r)
                )
            }
            comms.forEach { c ->
                operations.add(
                    firestore.collection(COLLECTION_COMMUNICATIONS).document(c.id) to commToFirestoreMap(c)
                )
            }

            val settingsMap = mapOf(
                "darkMode" to currentConfig.darkMode,
                "instituteName" to currentConfig.instituteName,
                "address" to currentConfig.address,
                "contactPhone" to currentConfig.contactPhone,
                "defaultClassesPerCycle" to currentConfig.defaultClassesPerCycle,
                "updatedAt" to getIsoNow()
            )
            operations.add(
                firestore.collection(COLLECTION_SETTINGS).document(DOC_INSTITUTE_CONFIG) to settingsMap
            )

            // Commit in chunks of 400 so lakhs of records sync safely
            operations.chunked(BATCH_CHUNK_SIZE).forEach { chunk ->
                val writeBatch = firestore.batch()
                chunk.forEach { (docRef, dataMap) ->
                    writeBatch.set(docRef, dataMap)
                }
                writeBatch.commit().await()
            }

            _isCloudConnected.value = true
            Result.success("Cloud Synced! (${students.size} students, ${payments.size} payments synced to Collections)")
        } catch (e: Exception) {
            _isCloudConnected.value = false
            Result.failure(e)
        } finally {
            isApplyingRemoteUpdate = false
        }
    }

    /**
     * Full two-way sync button handler.
     */
    suspend fun syncWithCloud(pullFirst: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        if (pullFirst) {
            pullFromCloud()
        } else {
            val localStudents = studentDao.getAllStudents().first()
            if (localStudents.isEmpty()) {
                val pullRes = pullFromCloud()
                if (pullRes.isSuccess) return@withContext pullRes
            }
            pushToCloud()
        }
    }

    private fun parseStudentMap(fallbackId: String, m: Map<String, Any?>): Student? {
        val id = m["id"]?.toString()?.ifBlank { fallbackId } ?: fallbackId
        val name = m["name"]?.toString() ?: return null
        val roll = (m["roll"] ?: m["rollNo"])?.toString() ?: ""
        val batchId = m["batchId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        val fee = (m["monthlyFee"] as? Number)?.toDouble() ?: m["monthlyFee"]?.toString()?.toDoubleOrNull() ?: 0.0
        return Student(
            id = id,
            rollNo = roll,
            name = name,
            phone = m["phone"]?.toString() ?: "",
            parentPhone = m["parentPhone"]?.toString() ?: "",
            batchId = batchId,
            monthlyFee = fee,
            joiningDate = m["joiningDate"]?.toString() ?: FeeCalculator.getTodayStr(),
            status = m["status"]?.toString() ?: "active",
            notes = m["notes"]?.toString() ?: ""
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseAttendanceDateDoc(
        dateDocId: String,
        data: Map<String, Any?>,
        studentBatchMap: Map<String, String?>
    ): List<AttendanceRecord> {
        val dateStr = data["date"]?.toString()?.ifBlank { dateDocId } ?: dateDocId
        val items = (data["records"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
        return items.mapNotNull { item ->
            val stuId = item["studentId"]?.toString() ?: return@mapNotNull null
            val status = item["status"]?.toString() ?: "present"
            val reason = item["reason"]?.toString() ?: ""
            val batchId = item["batchId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: studentBatchMap[stuId]
            AttendanceRecord(
                id = "${dateStr}_${stuId}",
                date = dateStr,
                studentId = stuId,
                batchId = batchId,
                status = status,
                reason = reason
            )
        }
    }

    private fun parsePaymentMap(fallbackId: String, m: Map<String, Any?>): PaymentRecord? {
        val txId = (m["transactionId"] ?: m["id"])?.toString()?.ifBlank { fallbackId } ?: fallbackId
        val stuId = m["studentId"]?.toString() ?: return null
        val amt = (m["amountPaid"] ?: m["amount"])?.let {
            (it as? Number)?.toDouble() ?: it.toString().toDoubleOrNull()
        } ?: 0.0
        val disc = m["discount"]?.let {
            (it as? Number)?.toDouble() ?: it.toString().toDoubleOrNull()
        } ?: 0.0
        val payDate = m["paymentDate"]?.toString() ?: FeeCalculator.getTodayStr()
        val mode = (m["mode"] ?: m["paymentMode"])?.toString() ?: "Cash"
        val remarks = m["remarks"]?.toString() ?: ""
        val isoTs = m["timestamp"]?.toString() ?: ""
        val tsMillis = (m["timestampMillis"] as? Number)?.toLong() ?: parseIsoToMillis(isoTs)
        return PaymentRecord(
            id = txId,
            studentId = stuId,
            amount = amt,
            discount = disc,
            paymentDate = payDate,
            paymentMode = mode,
            remarks = remarks,
            timestamp = tsMillis,
            isoTimestamp = isoTs
        )
    }

    private fun parseStaffMap(fallbackId: String, m: Map<String, Any?>): Staff? {
        val id = m["id"]?.toString()?.ifBlank { fallbackId } ?: fallbackId
        val name = m["name"]?.toString() ?: return null
        return Staff(
            id = id,
            name = name,
            role = m["role"]?.toString() ?: "Teacher",
            subject = m["subject"]?.toString() ?: "",
            phone = m["phone"]?.toString() ?: "",
            salary = (m["salary"] as? Number)?.toDouble() ?: m["salary"]?.toString()?.toDoubleOrNull() ?: 0.0
        )
    }

    private fun parseBatchMap(fallbackId: String, m: Map<String, Any?>, staffById: Map<String, Staff>): Batch? {
        val id = m["id"]?.toString()?.ifBlank { fallbackId } ?: fallbackId
        val name = m["name"]?.toString() ?: return null
        val teacherId = m["teacherId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        val teacherName = teacherId?.let { staffById[it]?.name } ?: (m["teacherName"]?.toString() ?: "")
        val classesPerCycle = (m["classesPerCycle"] as? Number)?.toInt() ?: m["classesPerCycle"]?.toString()?.toIntOrNull() ?: 0
        return Batch(
            id = id,
            name = name,
            subject = m["subject"]?.toString() ?: "",
            schedule = m["schedule"]?.toString() ?: "",
            timing = m["timing"]?.toString() ?: "",
            teacherId = teacherId,
            teacherName = teacherName,
            classesPerCycle = classesPerCycle
        )
    }

    private fun parseExpenseMap(fallbackId: String, m: Map<String, Any?>): ExpenseRecord {
        val id = m["id"]?.toString()?.ifBlank { fallbackId } ?: fallbackId
        val date = m["date"]?.toString() ?: FeeCalculator.getTodayStr()
        val category = m["category"]?.toString() ?: "Other"
        val description = m["description"]?.toString() ?: ""
        val amount = (m["amount"] as? Number)?.toDouble() ?: m["amount"]?.toString()?.toDoubleOrNull() ?: 0.0
        return ExpenseRecord(
            id = id,
            date = date,
            category = category,
            description = description,
            amount = amount
        )
    }

    private fun parseExamMap(fallbackId: String, m: Map<String, Any?>): Exam? {
        val id = m["id"]?.toString()?.ifBlank { fallbackId } ?: fallbackId
        val name = m["name"]?.toString() ?: return null
        val batchId = m["batchId"]?.toString() ?: ""
        val date = m["date"]?.toString() ?: FeeCalculator.getTodayStr()
        val totalMarks = (m["totalMarks"] as? Number)?.toInt() ?: m["totalMarks"]?.toString()?.toIntOrNull() ?: 100
        return Exam(
            id = id,
            name = name,
            batchId = batchId,
            date = date,
            totalMarks = totalMarks
        )
    }

    private fun parseExamResultMap(m: Map<String, Any?>): ExamResult? {
        val examId = m["examId"]?.toString() ?: return null
        val studentId = m["studentId"]?.toString() ?: return null
        val marks = (m["marksObtained"] as? Number)?.toDouble() ?: m["marksObtained"]?.toString()?.toDoubleOrNull() ?: 0.0
        return ExamResult(
            id = "${examId}_${studentId}",
            examId = examId,
            studentId = studentId,
            marksObtained = marks
        )
    }

    private fun parseCommMap(fallbackId: String, m: Map<String, Any?>): CommunicationLog? {
        val id = m["id"]?.toString()?.ifBlank { fallbackId } ?: fallbackId
        val stuId = m["studentId"]?.toString() ?: return null
        val note = m["note"]?.toString() ?: ""
        val date = m["date"]?.toString() ?: FeeCalculator.getTodayStr()
        val isoTs = m["timestamp"]?.toString() ?: ""
        return CommunicationLog(
            id = id,
            studentId = stuId,
            date = date,
            note = note,
            timestamp = parseIsoToMillis(isoTs),
            isoTimestamp = isoTs
        )
    }

    private fun parseSettingsMap(rawSettings: Map<String, Any?>, existingConfig: InstituteConfig): InstituteConfig {
        val parsedCycle = (rawSettings["defaultClassesPerCycle"] as? Number)?.toInt()
            ?: rawSettings["defaultClassesPerCycle"]?.toString()?.toIntOrNull()
            ?: existingConfig.defaultClassesPerCycle
        return existingConfig.copy(
            instituteName = rawSettings["instituteName"]?.toString()?.ifBlank { "CoachMaster" } ?: existingConfig.instituteName,
            address = rawSettings["address"]?.toString() ?: existingConfig.address,
            contactPhone = rawSettings["contactPhone"]?.toString() ?: existingConfig.contactPhone,
            darkMode = (rawSettings["darkMode"] as? Boolean) ?: existingConfig.darkMode,
            defaultClassesPerCycle = if (parsedCycle > 0) parsedCycle else 30,
            adminPin = SecurityUtils.hashSecret(existingConfig.adminPin),
            adminPassword = SecurityUtils.hashSecret(existingConfig.adminPassword)
        )
    }

    private suspend fun buildCloudPayloadMap(): Map<String, Any?> {
        val students = studentDao.getAllStudents().first()
        val batches = batchDao.getAllBatches().first()
        val staffList = staffDao.getAllStaff().first()
        val attendanceList = attendanceDao.getAllAttendance().first()
        val payments = paymentDao.getAllPayments().first()
        val expenses = expenseDao.getAllExpenses().first()
        val exams = examDao.getAllExams().first()
        val examResults = examDao.getAllExamResults().first()
        val comms = commDao.getAllLogs().first()
        val currentConfig = configDao.getConfigSync() ?: InstituteConfig()

        val studentsMapped = students.map { s -> studentToFirestoreMap(s) }

        val attendanceByDate = mutableMapOf<String, List<Map<String, String>>>()
        attendanceList.groupBy { it.date }.forEach { (dateStr, records) ->
            attendanceByDate[dateStr] = records.map { r ->
                mapOf(
                    "studentId" to r.studentId,
                    "status" to r.status,
                    "reason" to r.reason
                )
            }
        }

        val paymentsMapped = payments.map { p -> paymentToFirestoreMap(p) }
        val batchesMapped = batches.map { b -> batchToFirestoreMap(b) }
        val commsMapped = comms.map { c -> commToFirestoreMap(c) }
        val examsMapped = exams.map { ex -> examToFirestoreMap(ex) }
        val examResultsMapped = examResults.map { r -> examResultToFirestoreMap(r) }
        val expensesMapped = expenses.map { e -> expenseToFirestoreMap(e) }
        val staffMapped = staffList.map { st -> staffToFirestoreMap(st) }

        val settingsMap = mapOf(
            "darkMode" to currentConfig.darkMode,
            "instituteName" to currentConfig.instituteName,
            "address" to currentConfig.address,
            "contactPhone" to currentConfig.contactPhone,
            "defaultClassesPerCycle" to currentConfig.defaultClassesPerCycle
        )

        return mapOf(
            "students" to studentsMapped,
            "attendance" to attendanceByDate,
            "payments" to paymentsMapped,
            "batches" to batchesMapped,
            "communications" to commsMapped,
            "exams" to examsMapped,
            "examResults" to examResultsMapped,
            "expenses" to expensesMapped,
            "staff" to staffMapped,
            "settings" to settingsMap
        )
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun applyCloudMapToLocalDb(data: Map<String, Any?>) {
        isApplyingRemoteUpdate = true
        try {
            val rawStaff = (data["staff"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
            val parsedStaff = rawStaff.mapNotNull { m ->
                val id = m["id"]?.toString() ?: return@mapNotNull null
                parseStaffMap(id, m)
            }
            val staffById = parsedStaff.associateBy { it.id }

            val rawBatches = (data["batches"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
            val parsedBatches = rawBatches.mapNotNull { m ->
                val id = m["id"]?.toString() ?: return@mapNotNull null
                parseBatchMap(id, m, staffById)
            }

            val rawStudents = (data["students"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
            val parsedStudents = rawStudents.mapNotNull { m ->
                val id = m["id"]?.toString() ?: return@mapNotNull null
                parseStudentMap(id, m)
            }
            val studentBatchMap = parsedStudents.associate { it.id to it.batchId }

            val parsedAttendance = mutableListOf<AttendanceRecord>()
            val rawAtt = data["attendance"]
            if (rawAtt is Map<*, *>) {
                rawAtt.forEach { (dateKey, dayList) ->
                    val dateStr = dateKey?.toString() ?: return@forEach
                    val items = (dayList as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
                    items.forEach { item ->
                        val stuId = item["studentId"]?.toString() ?: return@forEach
                        val status = item["status"]?.toString() ?: "present"
                        val reason = item["reason"]?.toString() ?: ""
                        parsedAttendance.add(
                            AttendanceRecord(
                                id = "${dateStr}_${stuId}",
                                date = dateStr,
                                studentId = stuId,
                                batchId = studentBatchMap[stuId],
                                status = status,
                                reason = reason
                            )
                        )
                    }
                }
            }

            val rawPayments = (data["payments"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
            val parsedPayments = rawPayments.mapNotNull { m ->
                val id = (m["transactionId"] ?: m["id"])?.toString() ?: UUID.randomUUID().toString()
                parsePaymentMap(id, m)
            }

            val rawComms = (data["communications"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
            val parsedComms = rawComms.mapNotNull { m ->
                val id = m["id"]?.toString() ?: UUID.randomUUID().toString()
                parseCommMap(id, m)
            }

            val rawExams = (data["exams"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
            val parsedExams = rawExams.mapNotNull { m ->
                val id = m["id"]?.toString() ?: return@mapNotNull null
                parseExamMap(id, m)
            }

            val rawResults = (data["examResults"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
            val parsedResults = rawResults.mapNotNull { m -> parseExamResultMap(m) }

            val rawExpenses = (data["expenses"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
            val parsedExpenses = rawExpenses.mapNotNull { m ->
                val id = m["id"]?.toString() ?: UUID.randomUUID().toString()
                parseExpenseMap(id, m)
            }

            val rawSettings = data["settings"] as? Map<String, Any?>
            val existingConfig = configDao.getConfigSync() ?: InstituteConfig()
            val updatedConfig = if (rawSettings != null) {
                parseSettingsMap(rawSettings, existingConfig)
            } else existingConfig

            database.withTransaction {
                studentDao.deleteAll()
                batchDao.deleteAll()
                staffDao.deleteAll()
                attendanceDao.deleteAll()
                paymentDao.deleteAll()
                commDao.deleteAll()
                examDao.deleteAllExamResults()
                examDao.deleteAllExams()
                expenseDao.deleteAll()

                staffDao.insertStaffList(parsedStaff)
                batchDao.insertBatches(parsedBatches)
                studentDao.insertStudents(parsedStudents)
                attendanceDao.insertAttendanceList(parsedAttendance)
                paymentDao.insertPayments(parsedPayments)
                commDao.insertLogs(parsedComms)
                examDao.insertExams(parsedExams)
                examDao.insertResults(parsedResults)
                expenseDao.insertExpenses(parsedExpenses)
                configDao.saveConfig(updatedConfig)
            }
        } finally {
            isApplyingRemoteUpdate = false
        }
    }

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val payload = buildCloudPayloadMap()
        JSONObject(payload).toString(2)
    }

    suspend fun importBackupJson(jsonStr: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonStr)
            if (!root.has("students") || !root.has("attendance") || !root.has("payments")) {
                return@withContext Result.failure(IllegalArgumentException("Invalid backup schema"))
            }
            val map = jsonObjectToMap(root)
            applyCloudMapToLocalDb(map)
            pushToCloud()
            Result.success("Database restored and synced to Firestore Collections!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.get(key)
            map[key] = when (value) {
                JSONObject.NULL -> null
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                else -> value
            }
        }
        return map
    }

    private fun jsonArrayToList(arr: JSONArray): List<Any?> {
        val list = mutableListOf<Any?>()
        for (i in 0 until arr.length()) {
            val value = arr.get(i)
            list.add(
                when (value) {
                    JSONObject.NULL -> null
                    is JSONObject -> jsonObjectToMap(value)
                    is JSONArray -> jsonArrayToList(value)
                    else -> value
                }
            )
        }
        return list
    }
}
