package com.example.mydmucabapp_passenger.adopters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.mydmucabapp_passenger.databinding.ItemFaqBinding
import com.example.mydmucabapp_passenger.model.DataClass.Faq

class FaqsAdapter(private val faqsList: List<Faq>, val context: Context) : RecyclerView.Adapter<FaqsAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemFaqBinding) : RecyclerView.ViewHolder(binding.root){
        fun bind(faq: Faq){
            binding.question.text = faq.question
            binding.answer.text = faq.answer
            binding.parent.setOnClickListener {
                if (binding.answer.isVisible){
                    binding.icUp.visibility = View.GONE
                    binding.icDown.visibility = View.VISIBLE
                    binding.answer.startAnimation(hideAnim(binding.answer))
                }else{
                    binding.icDown.visibility = View.GONE
                    binding.icUp.visibility = View.VISIBLE
                    binding.answer.startAnimation(showAnim(binding.answer))
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemFaqBinding.inflate(LayoutInflater.from(parent.context),parent,false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val faq = faqsList[position]
        holder.bind(faq)
    }

    override fun getItemCount(): Int {
        return faqsList.size
    }

    private fun hideAnim(v: View): Animation {
        val initialHeight = v.getMeasuredHeight();
        val a: Animation = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                if (interpolatedTime == 1f) {
                    v.setVisibility(View.GONE)
                } else {
                    v.getLayoutParams().height =
                        initialHeight - (initialHeight * interpolatedTime).toInt()
                    v.requestLayout()
                }
            }

            override fun willChangeBounds(): Boolean {
                return true
            }
        }
        a.duration = 250
        return a
    }
    private fun showAnim(v: View): Animation {
        val matchParentMeasureSpec =
            View.MeasureSpec.makeMeasureSpec((v.parent as View).width, View.MeasureSpec.EXACTLY)
        val wrapContentMeasureSpec =
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        v.measure(matchParentMeasureSpec, wrapContentMeasureSpec)
        val targetHeight = v.measuredHeight
        v.layoutParams.height = 1
        v.visibility = View.VISIBLE
        val a: Animation = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                v.layoutParams.height =
                    if (interpolatedTime == 1f) LinearLayout.LayoutParams.WRAP_CONTENT else (targetHeight * interpolatedTime).toInt()
                v.requestLayout()
            }

            override fun willChangeBounds(): Boolean {
                return true
            }
        }
        a.duration = 250
        return a
    }

}