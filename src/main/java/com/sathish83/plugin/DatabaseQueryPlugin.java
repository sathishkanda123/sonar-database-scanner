package com.sathish83.plugin;

import com.sathish83.sensor.DatabaseQuerySensor;
import org.sonar.api.Plugin;

public class DatabaseQueryPlugin implements Plugin {

    @Override
    public void define(Context context) {
        context.addExtension(DatabaseQuerySensor.class);
    }
}
