package com.omnixone.ethernetipdemoapp;

import com.omnixone.modbuslibrary.procimg.SimpleRegister;

public class ObservableRegister extends SimpleRegister {

    private OnValueChangedListener listener;

    public ObservableRegister(int value) {
        super(value);
    }

    public void setOnValueChangedListener(OnValueChangedListener listener) {
        this.listener = listener;
    }

    @Override
    public void setValue(int value) {
        super.setValue(value);
        if (listener != null) {
            listener.onValueChanged(value);
        }
    }

    public interface OnValueChangedListener {
        void onValueChanged(int newValue);
    }
}
