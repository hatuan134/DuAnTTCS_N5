package com.duanttcsn5.library.entity;

public enum PhysicalCondition {
    NEW("Mới"), GOOD("Tốt"), OLD("Cũ"),
    LIGHTLY_DAMAGED("Hư hỏng nhẹ"), HEAVILY_DAMAGED("Hư hỏng nặng");

    private final String label;
    PhysicalCondition(String label) { this.label = label; }
    public String getLabel() { return label; }
}
