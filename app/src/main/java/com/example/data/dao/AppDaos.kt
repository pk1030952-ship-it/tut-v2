package com.example.data.dao

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY name ASC")
    fun getAllStudents(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE status = 'active' ORDER BY name ASC")
    fun getActiveStudents(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    fun getStudentById(id: String): Flow<Student?>

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    suspend fun getStudentByIdSync(id: String): Student?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: Student)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudents(students: List<Student>)

    @Update
    suspend fun updateStudent(student: Student)

    @Delete
    suspend fun deleteStudent(student: Student)

    @Query("DELETE FROM students WHERE id = :id")
    suspend fun deleteStudentById(id: String)

    @Query("DELETE FROM students")
    suspend fun deleteAll()
}

@Dao
interface BatchDao {
    @Query("SELECT * FROM batches ORDER BY name ASC")
    fun getAllBatches(): Flow<List<Batch>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: Batch)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatches(batches: List<Batch>)

    @Delete
    suspend fun deleteBatch(batch: Batch)

    @Query("DELETE FROM batches WHERE id = :id")
    suspend fun deleteBatchById(id: String)

    @Query("DELETE FROM batches")
    suspend fun deleteAll()
}

@Dao
interface StaffDao {
    @Query("SELECT * FROM staff ORDER BY name ASC")
    fun getAllStaff(): Flow<List<Staff>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStaff(staff: Staff)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStaffList(staffList: List<Staff>)

    @Delete
    suspend fun deleteStaff(staff: Staff)

    @Query("DELETE FROM staff WHERE id = :id")
    suspend fun deleteStaffById(id: String)

    @Query("DELETE FROM staff")
    suspend fun deleteAll()
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance ORDER BY date DESC")
    fun getAllAttendance(): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance WHERE date = :date")
    fun getAttendanceForDate(date: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance WHERE studentId = :studentId ORDER BY date DESC")
    fun getAttendanceForStudent(studentId: String): Flow<List<AttendanceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceList(records: List<AttendanceRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(record: AttendanceRecord)

    @Query("DELETE FROM attendance WHERE date = :date")
    suspend fun deleteForDate(date: String)

    @Query("DELETE FROM attendance WHERE studentId = :studentId")
    suspend fun deleteForStudent(studentId: String)

    @Query("DELETE FROM attendance")
    suspend fun deleteAll()
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY paymentDate DESC, timestamp DESC")
    fun getAllPayments(): Flow<List<PaymentRecord>>

    @Query("SELECT * FROM payments WHERE studentId = :studentId ORDER BY paymentDate DESC, timestamp DESC")
    fun getPaymentsForStudent(studentId: String): Flow<List<PaymentRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<PaymentRecord>)

    @Delete
    suspend fun deletePayment(payment: PaymentRecord)

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun deletePaymentById(id: String)

    @Query("DELETE FROM payments WHERE studentId = :studentId")
    suspend fun deleteForStudent(studentId: String)

    @Query("DELETE FROM payments")
    suspend fun deleteAll()
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<ExpenseRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseRecord>)

    @Delete
    suspend fun deleteExpense(expense: ExpenseRecord)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: String)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams ORDER BY date DESC")
    fun getAllExams(): Flow<List<Exam>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(exam: Exam)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExams(exams: List<Exam>)

    @Delete
    suspend fun deleteExam(exam: Exam)

    @Query("DELETE FROM exams WHERE id = :id")
    suspend fun deleteExamById(id: String)

    @Query("DELETE FROM exams")
    suspend fun deleteAllExams()

    @Query("SELECT * FROM exam_results")
    fun getAllExamResults(): Flow<List<ExamResult>>

    @Query("SELECT * FROM exam_results WHERE examId = :examId")
    fun getResultsForExam(examId: String): Flow<List<ExamResult>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResults(results: List<ExamResult>)

    @Query("DELETE FROM exam_results WHERE examId = :examId")
    suspend fun deleteResultsForExam(examId: String)

    @Query("DELETE FROM exam_results WHERE studentId = :studentId")
    suspend fun deleteResultsForStudent(studentId: String)

    @Query("DELETE FROM exam_results")
    suspend fun deleteAllExamResults()
}

@Dao
interface CommunicationDao {
    @Query("SELECT * FROM communication_logs ORDER BY date DESC, timestamp DESC")
    fun getAllLogs(): Flow<List<CommunicationLog>>

    @Query("SELECT * FROM communication_logs WHERE studentId = :studentId ORDER BY date DESC, timestamp DESC")
    fun getLogsForStudent(studentId: String): Flow<List<CommunicationLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: CommunicationLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<CommunicationLog>)

    @Delete
    suspend fun deleteLog(log: CommunicationLog)

    @Query("DELETE FROM communication_logs WHERE id = :id")
    suspend fun deleteLogById(id: String)

    @Query("DELETE FROM communication_logs WHERE studentId = :studentId")
    suspend fun deleteForStudent(studentId: String)

    @Query("DELETE FROM communication_logs")
    suspend fun deleteAll()
}

@Dao
interface InstituteConfigDao {
    @Query("SELECT * FROM institute_config WHERE id = 1 LIMIT 1")
    fun getConfig(): Flow<InstituteConfig?>

    @Query("SELECT * FROM institute_config WHERE id = 1 LIMIT 1")
    suspend fun getConfigSync(): InstituteConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: InstituteConfig)
}
