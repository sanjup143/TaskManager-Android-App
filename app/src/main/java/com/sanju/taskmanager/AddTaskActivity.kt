package com.sanju.taskmanager

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sanju.taskmanager.database.TaskDatabase
import com.sanju.taskmanager.databinding.ActivityAddTaskBinding
import com.sanju.taskmanager.model.Task
import com.sanju.taskmanager.repository.TaskRepository
import com.sanju.taskmanager.viewmodel.TaskViewModel
import com.sanju.taskmanager.viewmodel.TaskViewModelFactory
import com.sanju.taskmanager.worker.TaskReminderWorker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class AddTaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTaskBinding
    private lateinit var taskViewModel: TaskViewModel

    private var taskId: Int = 0
    private var isEditMode: Boolean = false
    private var isCompleted: Boolean = false
    private var selectedDueDate: String = ""

    private val priorityOptions = listOf("High", "Medium", "Low")
    private val categoryOptions = listOf("Work", "Personal", "Study", "Shopping")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        setupDropdowns()
        checkEditMode()

        binding.etDueDate.setOnClickListener {
            showDatePickerDialog()
        }

        binding.tilDueDate.setEndIconOnClickListener {
            showDatePickerDialog()
        }

        binding.btnSaveTask.setOnClickListener {
            saveTask()
        }
    }

    private fun setupDropdowns() {
        val priorityAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            priorityOptions
        )

        val categoryAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            categoryOptions
        )

        binding.etPriority.setAdapter(priorityAdapter)
        binding.etCategory.setAdapter(categoryAdapter)
    }

    private fun setupViewModel() {
        val taskDao = TaskDatabase.getDatabase(applicationContext).taskDao()
        val repository = TaskRepository(taskDao)
        val factory = TaskViewModelFactory(repository)

        taskViewModel = ViewModelProvider(this, factory)[TaskViewModel::class.java]
    }

    private fun checkEditMode() {
        taskId = intent.getIntExtra("task_id", 0)

        if (taskId != 0) {
            isEditMode = true

            val title = intent.getStringExtra("task_title")
            val description = intent.getStringExtra("task_description")
            val priority = intent.getStringExtra("task_priority")
            val category = intent.getStringExtra("task_category")
            val dueDate = intent.getStringExtra("task_due_date")
            isCompleted = intent.getBooleanExtra("task_is_completed", false)

            selectedDueDate = dueDate ?: ""

            binding.tvAddTaskTitle.text = getString(R.string.edit_task)
            binding.btnSaveTask.text = getString(R.string.update_task)

            binding.etTaskTitle.setText(title)
            binding.etTaskDescription.setText(description)
            binding.etPriority.setText(priority, false)
            binding.etCategory.setText(category, false)
            binding.etDueDate.setText(selectedDueDate)
        }
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()

        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(year, month, dayOfMonth)

                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                selectedDueDate = dateFormat.format(selectedCalendar.time)

                binding.etDueDate.setText(selectedDueDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        datePickerDialog.show()
    }

    private fun saveTask() {
        val title = binding.etTaskTitle.text.toString().trim()
        val description = binding.etTaskDescription.text.toString().trim()
        val priority = binding.etPriority.text.toString().trim()
        val category = binding.etCategory.text.toString().trim()
        val dueDate = binding.etDueDate.text.toString().trim()

        if (title.isEmpty()) {
            binding.etTaskTitle.error = "Task title is required"
            return
        }

        if (description.isEmpty()) {
            binding.etTaskDescription.error = "Task description is required"
            return
        }

        if (priority.isEmpty()) {
            binding.etPriority.error = "Priority is required"
            return
        }

        if (category.isEmpty()) {
            binding.etCategory.error = "Category is required"
            return
        }

        if (dueDate.isEmpty()) {
            binding.etDueDate.error = "Due date is required"
            return
        }

        val task = Task(
            id = taskId,
            title = title,
            description = description,
            priority = priority,
            category = category,
            dueDate = dueDate,
            isCompleted = isCompleted
        )

        if (isEditMode) {
            taskViewModel.updateTask(task)
            scheduleDueDateReminder(title, dueDate)
            Toast.makeText(this, "Task updated successfully", Toast.LENGTH_SHORT).show()
        } else {
            taskViewModel.insertTask(task)
            scheduleDueDateReminder(title, dueDate)
            Toast.makeText(this, "Task saved successfully", Toast.LENGTH_SHORT).show()
        }

        finish()
    }

    private fun scheduleDueDateReminder(taskTitle: String, dueDate: String) {
        val delay = calculateDelayInMillis(dueDate)

        if (delay <= 0L) {
            Toast.makeText(this, "Due date has already passed", Toast.LENGTH_SHORT).show()
            return
        }

        val inputData = Data.Builder()
            .putString("task_title", taskTitle)
            .build()

        val reminderRequest = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInputData(inputData)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(applicationContext).enqueue(reminderRequest)
    }

    private fun calculateDelayInMillis(dueDate: String): Long {
        return try {
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val selectedDate = dateFormat.parse(dueDate) ?: return 0L

            val reminderCalendar = Calendar.getInstance()
            reminderCalendar.time = selectedDate
            reminderCalendar.set(Calendar.HOUR_OF_DAY, 9)
            reminderCalendar.set(Calendar.MINUTE, 0)
            reminderCalendar.set(Calendar.SECOND, 0)
            reminderCalendar.set(Calendar.MILLISECOND, 0)

            reminderCalendar.timeInMillis - System.currentTimeMillis()
        } catch (_: Exception) {
            0L
        }
    }
}