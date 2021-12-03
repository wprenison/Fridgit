package io.flyingmongoose.fridgit.ui.fridge

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import io.flyingmongoose.fridgit.R
import io.flyingmongoose.fridgit.mlkit.LiveBarcodeScanningActivity

class FridgeFragment : Fragment()
{

    private lateinit var fridgeViewModel: FridgeViewModel

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View?
    {
        fridgeViewModel =
                ViewModelProvider(this).get(FridgeViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_fridge, container, false)
        val textView: TextView = root.findViewById(R.id.text_dashboard)
        fridgeViewModel.text.observe(viewLifecycleOwner, Observer {
            textView.text = it
        })

        return root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }
}