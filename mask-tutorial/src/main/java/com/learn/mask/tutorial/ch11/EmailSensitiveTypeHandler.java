package com.learn.mask.tutorial.ch11;

import com.learn.mask.tutorial.ch04.SensitiveType;

public class EmailSensitiveTypeHandler extends SensitiveTypeHandler {
    public EmailSensitiveTypeHandler() {
        super(SensitiveType.EMAIL);
    }
}
