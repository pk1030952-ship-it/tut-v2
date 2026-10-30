package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.util.FeeCalculator
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
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

    @Volatile
    private var isApplyingRemoteUpdate = false
    private var snapshotListener: ListenerRegistration? = null

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

    /**
     * Ensures FirebaseApp is initialized with the exact project credentials from v10studendrecord.html
     * (projectId: tution-pro-32a33, collection: tuitionAppDb, doc: mainData).
     */
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
     * Starts real-time Firestore listener on `tuitionAppDb/mainData` so changes made in
     * v10studendrecord.html or on another device appear automatically in the app.
     */
    fun startRealtimeCloudSync(onStatusChange: (Boolean, String?) -> Unit = { _, _ -> }) {
        val firestore = getFirestoreSafely() ?: return
        if (snapshotListener != null) return

        val docRef = firestore.collection("tuitionAppDb").document("mainData")
        snapshotListener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onStatusChange(false, null)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists() && !isApplyingRemoteUpdate) {
                val data = snapshot.data
                if (data != null) {
                    repoScope.launch {
                        applyCloudMapToLocalDb(data)
                        onStatusChange(true, null)
                    }
                }
            }
        }
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

    // Student CRUD
    suspend fun saveStudent(student: Student) = withContext(Dispatchers.IO) {
        studentDao.insertStudent(student)
        pushToCloud()
    }

    suspend fun deleteStudent(studentId: String) = withContext(Dispatchers.IO) {
        studentDao.deleteStudentById(studentId)
        attendanceDao.deleteForStudent(studentId)
        paymentDao.deleteForStudent(studentId)
        commDao.deleteForStudent(studentId)
        examDao.deleteResultsForStudent(studentId)
        pushToCloud()
    }

    suspend fun toggleStudentStatus(student: Student) = withContext(Dispatchers.IO) {
        val newStatus = if (student.status == "active") "inactive" else "active"
        studentDao.updateStudent(student.copy(status = newStatus))
        pushToCloud()
    }

    // Batch CRUD
    suspend fun saveBatch(batch: Batch) = withContext(Dispatchers.IO) {
        batchDao.insertBatch(batch)
        pushToCloud()
    }

    suspend fun deleteBatch(batch: Batch) = withContext(Dispatchers.IO) {
        batchDao.deleteBatch(batch)
        // Unassign students in this batch (matching v10studendrecord.html behavior)
        val enrolled = studentDao.getAllStudents().first().filter { it.batchId == batch.id }
        enrolled.forEach { s ->
            studentDao.updateStudent(s.copy(batchId = null))
        }
        pushToCloud()
    }

    // Staff CRUD
    suspend fun saveStaff(staff: Staff) = withContext(Dispatchers.IO) {
        staffDao.insertStaff(staff)
        pushToCloud()
    }

    suspend fun deleteStaff(staff: Staff) = withContext(Dispatchers.IO) {
        staffDao.deleteStaff(staff)
        // Unassign teacher from batches (matching v10studendrecord.html behavior)
        val assignedBatches = batchDao.getAllBatches().first().filter { it.teacherId == staff.id }
        assignedBatches.forEach { b ->
            batchDao.insertBatch(b.copy(teacherId = null, teacherName = ""))
        }
        pushToCloud()
    }

    // Attendance
    suspend fun recordAttendanceBatch(date: String, records: List<AttendanceRecord>) = withContext(Dispatchers.IO) {
        attendanceDao.insertAttendanceList(records)
        pushToCloud()
    }

    // Payment
    suspend fun recordPayment(payment: PaymentRecord) = withContext(Dispatchers.IO) {
        val withIso = if (payment.isoTimestamp.isBlank()) {
            payment.copy(isoTimestamp = getIsoNow())
        } else payment
        paymentDao.insertPayment(withIso)
        pushToCloud()
    }

    suspend fun deletePayment(payment: PaymentRecord) = withContext(Dispatchers.IO) {
        paymentDao.deletePayment(payment)
        pushToCloud()
    }

    // Expense
    suspend fun recordExpense(expense: ExpenseRecord) = withContext(Dispatchers.IO) {
        expenseDao.insertExpense(expense)
        pushToCloud()
    }

    suspend fun deleteExpense(expense: ExpenseRecord) = withContext(Dispatchers.IO) {
        expenseDao.deleteExpense(expense)
        pushToCloud()
    }

    // Exam & Results
    suspend fun saveExam(exam: Exam) = withContext(Dispatchers.IO) {
        examDao.insertExam(exam)
        pushToCloud()
    }

    suspend fun deleteExam(exam: Exam) = withContext(Dispatchers.IO) {
        examDao.deleteResultsForExam(exam.id)
        examDao.deleteExam(exam)
        pushToCloud()
    }

    suspend fun saveExamResults(examId: String, results: List<ExamResult>) = withContext(Dispatchers.IO) {
        examDao.deleteResultsForExam(examId)
        examDao.insertResults(results)
        pushToCloud()
    }

    // Communication Log
    suspend fun addCommunicationLog(log: CommunicationLog) = withContext(Dispatchers.IO) {
        val withIso = if (log.isoTimestamp.isBlank()) {
            log.copy(isoTimestamp = getIsoNow())
        } else log
        commDao.insertLog(withIso)
        pushToCloud()
    }

    suspend fun deleteCommunicationLog(log: CommunicationLog) = withContext(Dispatchers.IO) {
        commDao.deleteLog(log)
        pushToCloud()
    }

    // Config
    suspend fun saveConfig(config: InstituteConfig) = withContext(Dispatchers.IO) {
        configDao.saveConfig(config)
        pushToCloud()
    }

    // Factory Reset (clears both local Room DB and Firebase tuitionAppDb/mainData)
    suspend fun factoryReset() = withContext(Dispatchers.IO) {
        val currentConfig = configDao.getConfigSync() ?: InstituteConfig()
        database.clearAllTables()
        val resetConfig = currentConfig.copy(
            id = 1,
            instituteName = currentConfig.instituteName.ifBlank { "CoachMaster" },
            address = currentConfig.address
        )
        configDao.saveConfig(resetConfig)
        pushToCloud()
    }

    // Demo Data Seeder (matches v10studendrecord.html loadDemoData)
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

        // Seed 35 days of demo attendance for Rahul (32 working days + 3 holidays -> 1 completed cycle + 2/30 progress)
        // and 24 working days + 2 holidays for Priya (24/30 progress -> 6 classes left)
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
     * Pulls full data from Firebase `tuitionAppDb/mainData` into Room database.
     */
    suspend fun pullFromCloud(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val firestore = getFirestoreSafely()
                ?: return@withContext Result.failure(IllegalStateException("Firebase not available"))
            val snap = firestore.collection("tuitionAppDb").document("mainData").get().await()
            if (snap.exists() && snap.data != null) {
                applyCloudMapToLocalDb(snap.data!!)
                val count = studentDao.getAllStudents().first().size
                Result.success("Synced from Firebase Cloud ($count students loaded)")
            } else {
                // Document doesn't exist yet on cloud; push current local state
                pushToCloud()
                Result.success("Initialized Firebase Cloud database")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Pushes full local state to Firebase `tuitionAppDb/mainData` in the exact format of v10studendrecord.html.
     */
    suspend fun pushToCloud(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val firestore = getFirestoreSafely()
                ?: return@withContext Result.failure(IllegalStateException("Firebase not available"))
            isApplyingRemoteUpdate = true

            val payload = buildCloudPayloadMap()
            firestore.collection("tuitionAppDb")
                .document("mainData")
                .set(payload)
                .await()

            val studentCount = (payload["students"] as? List<*>)?.size ?: 0
            Result.success("Cloud Synced! ($studentCount students saved to Firebase)")
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            isApplyingRemoteUpdate = false
        }
    }

    /**
     * Full two-way sync button handler: pulls latest if local is empty, otherwise pushes local state,
     * or pulls if explicitly requested.
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

        val studentsMapped = students.map { s ->
            mapOf(
                "id" to s.id,
                "roll" to s.rollNo,
                "name" to s.name,
                "phone" to s.phone,
                "parentPhone" to s.parentPhone,
                "batchId" to s.batchId,
                "monthlyFee" to s.monthlyFee,
                "joiningDate" to s.joiningDate,
                "status" to s.status
            )
        }

        // Attendance in v10studendrecord.html is { "YYYY-MM-DD": [ { studentId, status, reason } ] }
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

        val paymentsMapped = payments.map { p ->
            mapOf(
                "transactionId" to p.id,
                "studentId" to p.studentId,
                "amountPaid" to p.amount,
                "paymentDate" to p.paymentDate,
                "mode" to p.paymentMode,
                "remarks" to p.remarks,
                "timestamp" to p.isoTimestamp.ifBlank { getIsoNow() }
            )
        }

        val batchesMapped = batches.map { b ->
            mapOf(
                "id" to b.id,
                "name" to b.name,
                "subject" to b.subject,
                "schedule" to b.schedule,
                "timing" to b.timing,
                "teacherId" to b.teacherId,
                "classesPerCycle" to b.classesPerCycle
            )
        }

        val commsMapped = comms.map { c ->
            mapOf(
                "id" to c.id,
                "studentId" to c.studentId,
                "date" to c.date,
                "note" to c.note,
                "timestamp" to c.isoTimestamp.ifBlank { getIsoNow() }
            )
        }

        val examsMapped = exams.map { ex ->
            mapOf(
                "id" to ex.id,
                "name" to ex.name,
                "batchId" to ex.batchId,
                "date" to ex.date,
                "totalMarks" to ex.totalMarks
            )
        }

        val examResultsMapped = examResults.map { r ->
            mapOf(
                "examId" to r.examId,
                "studentId" to r.studentId,
                "marksObtained" to r.marksObtained
            )
        }

        val expensesMapped = expenses.map { e ->
            mapOf(
                "id" to e.id,
                "date" to e.date,
                "category" to e.category,
                "description" to e.description,
                "amount" to e.amount
            )
        }

        val staffMapped = staffList.map { st ->
            mapOf(
                "id" to st.id,
                "name" to st.name,
                "role" to st.role,
                "subject" to st.subject,
                "phone" to st.phone,
                "salary" to st.salary
            )
        }

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
            // Parse Staff first so Batch teacher names can be linked
            val rawStaff = (data["staff"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
            val parsedStaff = rawStaff.mapNotNull { m ->
                val id = m["id"]?.toString() ?: return@mapNotNull null
                val name = m["name"]?.toString() ?: return@mapNotNull null
                Staff(
                    id = id,
                    name = name,
                    role = m["role"]?.toString() ?: "Teacher",
                    subject = m["subject"]?.toString() ?: "",
                    phone = m["phone"]?.toString() ?: "",
                    salary = (m["salary"] as? Number)?.toDouble() ?: m["salary"]?.toString()?.toDoubleOrNull() ?: 0.0
                )
            }
            val staffById = parsedStaff.associateBy { it.id }

            // Parse Batches
            val rawBatches = (data["batches"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
            val parsedBatches = rawBatches.mapNotNull { m ->
                val id = m["id"]?.toString() ?: return@mapNotNull null
                val name = m["name"]?.toString() ?: return@mapNotNull null
                val teacherId = m["teacherId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                val teacherName = teacherId?.let { staffById[it]?.name } ?: (m["teacherName"]?.toString() ?: "")
                val classesPerCycle = (m["classesPerCycle"] as? Number)?.toInt() ?: m["classesPerCycle"]?.toString()?.toIntOrNull() ?: 0
                Batch(
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

            // Parse Students
            val rawStudents = (data["students"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
            val parsedStudents = rawStudents.mapNotNull { m ->
                val id = m["id"]?.toString() ?: return@mapNotNull null
                val name = m["name"]?.toString() ?: return@mapNotNull null
                val roll = (m["roll"] ?: m["rollNo"])?.toString() ?: ""
                val batchId = m["batchId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                val fee = (m["monthlyFee"] as? Number)?.toDouble() ?: m["monthlyFee"]?.toString()?.toDoubleOrNull() ?: 0.0
                Student(
                    id = id,
                    rollNo = roll,
                    name = name,
                    phone = m["phone"]?.toString() ?: "",
                    parentPhone = m["parentPhone"]?.toString() ?: "",
                    batchId = batchId,
                    monthlyFee = fee,
                    joiningDate = m["joiningDate"]?.toString() ?: FeeCalculator.getTodayStr(),
                    status = m["status"]?.toString() ?: "active"
                )
            }
            val studentBatchMap = parsedStudents.associate { it.id to it.batchId }

            // Parse Attendance (Map of date -> List of {studentId, status, reason})
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

            // Parse Payments
            val rawPayments = (data["payments"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
            val parsedPayments = rawPayments.mapNotNull { m ->
                val txId = (m["transactionId"] ?: m["id"])?.toString() ?: UUID.randomUUID().toString()
                val stuId = m["studentId"]?.toString() ?: return@mapNotNull null
                val amt = (m["amountPaid"] ?: m["amount"])?.let {
                    (it as? Number)?.toDouble() ?: it.toString().toDoubleOrNull()
                } ?: 0.0
                val payDate = m["paymentDate"]?.toString() ?: FeeCalculator.getTodayStr()
                val mode = (m["mode"] ?: m["paymentMode"])?.toString() ?: "Cash"
                val remarks = m["remarks"]?.toString() ?: ""
                val isoTs = m["timestamp"]?.toString() ?: ""
                PaymentRecord(
                    id = txId,
                    studentId = stuId,
                    amount = amt,
                    paymentDate = payDate,
                    paymentMode = mode,
                    remarks = remarks,
                    timestamp = parseIsoToMillis(isoTs),
                    isoTimestamp = isoTs
                )
            }

            // Parse Communications
            val rawComms = (data["communications"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
            val parsedComms = rawComms.mapNotNull { m ->
                val id = m["id"]?.toString() ?: UUID.randomUUID().toString()
                val stuId = m["studentId"]?.toString() ?: return@mapNotNull null
                val note = m["note"]?.toString() ?: ""
                val date = m["date"]?.toString() ?: FeeCalculator.getTodayStr()
                val isoTs = m["timestamp"]?.toString() ?: ""
                CommunicationLog(
                    id = id,
                    studentId = stuId,
                    date = date,
                    note = note,
                    timestamp = parseIsoToMillis(isoTs),
                    isoTimestamp = isoTs
                )
            }

            // Parse Exams
            val rawExams = (data["exams"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
            val parsedExams = rawExams.mapNotNull { m ->
                val id = m["id"]?.toString() ?: return@mapNotNull null
                val name = m["name"]?.toString() ?: return@mapNotNull null
                val batchId = m["batchId"]?.toString() ?: ""
                val date = m["date"]?.toString() ?: FeeCalculator.getTodayStr()
                val totalMarks = (m["totalMarks"] as? Number)?.toInt() ?: m["totalMarks"]?.toString()?.toIntOrNull() ?: 100
                Exam(
                    id = id,
                    name = name,
                    batchId = batchId,
                    date = date,
                    totalMarks = totalMarks
                )
            }

            // Parse Exam Results
            val rawResults = (data["examResults"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
            val parsedResults = rawResults.mapNotNull { m ->
                val examId = m["examId"]?.toString() ?: return@mapNotNull null
                val studentId = m["studentId"]?.toString() ?: return@mapNotNull null
                val marks = (m["marksObtained"] as? Number)?.toDouble() ?: m["marksObtained"]?.toString()?.toDoubleOrNull() ?: 0.0
                ExamResult(
                    id = "${examId}_${studentId}",
                    examId = examId,
                    studentId = studentId,
                    marksObtained = marks
                )
            }

            // Parse Expenses
            val rawExpenses = (data["expenses"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
            val parsedExpenses = rawExpenses.mapNotNull { m ->
                val id = m["id"]?.toString() ?: UUID.randomUUID().toString()
                val date = m["date"]?.toString() ?: FeeCalculator.getTodayStr()
                val category = m["category"]?.toString() ?: "Other"
                val description = m["description"]?.toString() ?: ""
                val amount = (m["amount"] as? Number)?.toDouble() ?: m["amount"]?.toString()?.toDoubleOrNull() ?: 0.0
                ExpenseRecord(
                    id = id,
                    date = date,
                    category = category,
                    description = description,
                    amount = amount
                )
            }

            // Parse Settings
            val rawSettings = data["settings"] as? Map<String, Any?>
            val existingConfig = configDao.getConfigSync() ?: InstituteConfig()
            val updatedConfig = if (rawSettings != null) {
                val parsedCycle = (rawSettings["defaultClassesPerCycle"] as? Number)?.toInt()
                    ?: rawSettings["defaultClassesPerCycle"]?.toString()?.toIntOrNull()
                    ?: existingConfig.defaultClassesPerCycle
                existingConfig.copy(
                    instituteName = rawSettings["instituteName"]?.toString()?.ifBlank { "CoachMaster" } ?: existingConfig.instituteName,
                    address = rawSettings["address"]?.toString() ?: existingConfig.address,
                    contactPhone = rawSettings["contactPhone"]?.toString() ?: existingConfig.contactPhone,
                    darkMode = (rawSettings["darkMode"] as? Boolean) ?: existingConfig.darkMode,
                    defaultClassesPerCycle = if (parsedCycle > 0) parsedCycle else 30
                )
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

    /**
     * Exports the entire database as a formatted JSON string identical to v10studendrecord.html's
     * "Download Backup JSON" feature.
     */
    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val payload = buildCloudPayloadMap()
        JSONObject(payload).toString(2)
    }

    /**
     * Restores the database from a v10studendrecord.html backup JSON string and syncs to Firebase.
     */
    suspend fun importBackupJson(jsonStr: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonStr)
            if (!root.has("students") || !root.has("attendance") || !root.has("payments")) {
                return@withContext Result.failure(IllegalArgumentException("Invalid backup schema"))
            }
            val map = jsonObjectToMap(root)
            applyCloudMapToLocalDb(map)
            pushToCloud()
            Result.success("Database restored and synced to Firebase Cloud!")
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
