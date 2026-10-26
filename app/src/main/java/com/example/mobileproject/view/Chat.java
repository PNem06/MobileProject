package com.example.mobileproject.view;
import com.example.mobileproject.navigation.MainTab;
public class Chat extends ForwardTabActivity {
    @Override protected MainTab destination() { return MainTab.MESSAGES; }
}
