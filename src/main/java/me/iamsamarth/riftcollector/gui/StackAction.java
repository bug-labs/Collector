package me.iamsamarth.riftcollector.gui;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** What a click on a stored stack does. Which click does what is set in the menu file. */
public enum StackAction {
    NONE, TAKE_ONE, TAKE_STACK, FILL_INVENTORY;

    static @Nullable StackAction parse(String raw) {
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
