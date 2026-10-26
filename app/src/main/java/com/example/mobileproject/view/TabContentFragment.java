package com.example.mobileproject.view;

import android.view.View;
import androidx.fragment.app.Fragment;
import com.example.mobileproject.MainActivity;

public abstract class TabContentFragment extends Fragment {
    protected TabContentFragment(int layout) { super(layout); }
    protected <T extends View> T findViewById(int id) { return requireView().findViewById(id); }
    protected boolean screenActive() { return isAdded() && getView() != null && isResumed(); }
    protected void backToExplore() { ((MainActivity) requireActivity()).openPage("home"); }
}
