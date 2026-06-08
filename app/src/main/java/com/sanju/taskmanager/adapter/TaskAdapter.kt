package com.sanju.taskmanager.adapter

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sanju.taskmanager.databinding.ItemTaskBinding
import com.sanju.taskmanager.model.Task
import android.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.sanju.taskmanager.R

class TaskAdapter(
    private val onEditClick: (Task) -> Unit,
    private val onDeleteClick: (Task) -> Unit,
    private val onCompletedClick: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private val taskList = mutableListOf<Task>()

    private fun isTaskOverdue(dueDate: String): Boolean {

        return try {

            val formatter =
                SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

            val taskDate = formatter.parse(dueDate)

            val today = Date()

            taskDate != null &&
                    taskDate.before(today)

        } catch (_: Exception) {
            false
        }
    }

    inner class TaskViewHolder(
        private val binding: ItemTaskBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(task: Task) {
            binding.cbTaskCompleted.text = task.title
            binding.cbTaskCompleted.isChecked = task.isCompleted
            binding.tvTaskDescription.text = task.description
            binding.tvTaskPriority.text = binding.root.context.getString(R.string.task_priority,task.priority)
            binding.tvTaskCategory.text = binding.root.context.getString(R.string.task_category, task.category)
            binding.tvTaskDueDate.text = binding.root.context.getString(R.string.task_due_date,task.dueDate)

            if (!task.isCompleted && isTaskOverdue(task.dueDate)) {

                binding.tvTaskStatus.visibility =
                    android.view.View.VISIBLE

                binding.tvTaskStatus.text = binding.root.context.getString(R.string.task_overdue)

                binding.tvTaskStatus.setTextColor(
                    Color.RED
                )

            } else {

                binding.tvTaskStatus.visibility =
                    android.view.View.GONE
            }

            if (task.isCompleted) {
                binding.cbTaskCompleted.paintFlags =
                    binding.cbTaskCompleted.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG

                binding.tvTaskDescription.paintFlags =
                    binding.tvTaskDescription.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG

                binding.root.alpha = 0.6f
            } else {
                binding.cbTaskCompleted.paintFlags =
                    binding.cbTaskCompleted.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()

                binding.tvTaskDescription.paintFlags =
                    binding.tvTaskDescription.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()

                binding.root.alpha = 1f
            }

            binding.cbTaskCompleted.setOnClickListener {
                onCompletedClick(task)
            }

            binding.btnEditTask.setOnClickListener {
                onEditClick(task)
            }

            binding.btnDeleteTask.setOnClickListener {
                onDeleteClick(task)
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: TaskViewHolder,
        position: Int
    ) {
        holder.bind(taskList[position])
    }

    override fun getItemCount(): Int {
        return taskList.size
    }

    fun submitList(newList: List<Task>) {
        taskList.clear()
        taskList.addAll(newList)
        notifyDataSetChanged()
    }

    fun getCurrentList(): List<Task> {
        return taskList
    }
}