package com.example.easymrcp.utils;

import com.example.easymrcp.common.SipContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Random;
import java.util.Vector;

@Component
public class SipUtils {
    private static final Random random = new Random((new Date()).getTime());
    @Autowired
    SipContext sipContext;

    public static String getGUID() {
        // counter++;
        // return guidPrefix+counter;
        int r = random.nextInt();
        r = (r < 0) ? 0 - r : r; // generate a positive number
        return Integer.toString(r);
    }

    public Vector<String> getSupportProtocols(Vector<String> formatsInRequest) {
        Vector<String> useProtocol = new Vector<>();
        for (String supportProtocol : sipContext.getSupportProtocols()) {
            if (formatsInRequest.contains(supportProtocol)) {
                useProtocol.add(supportProtocol);
                break;
            }
        }
        return useProtocol;
    }
}
