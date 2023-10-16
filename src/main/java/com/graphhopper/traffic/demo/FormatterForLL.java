/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.graphhopper.traffic.demo;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 *
 * @author altair
 */
public class FormatterForLL extends Formatter {

    @Override
    public String format(LogRecord record) {
        return record.getMessage()+"\n";
    }
}
