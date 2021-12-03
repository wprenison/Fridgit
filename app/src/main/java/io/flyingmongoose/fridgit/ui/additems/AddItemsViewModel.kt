package io.flyingmongoose.fridgit.ui.additems

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class AddItemsViewModel : ViewModel()
{

    private val _text = MutableLiveData<String>().apply {
        value = "This is Add Items Fragment"
    }
    val text: LiveData<String> = _text
}