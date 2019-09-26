package com.stripeterminal;

import android.content.Context;

import com.stripe.stripeterminal.Terminal;
import com.stripe.stripeterminal.ConnectionTokenCallback;
import com.stripe.stripeterminal.ConnectionTokenProvider;
import com.stripe.stripeterminal.ConnectionTokenException;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import android.util.Log;

public class TokenProvider implements ConnectionTokenProvider {

    public static ReactApplicationContext mContext;
  
    @Override
    public void fetchConnectionToken(ConnectionTokenCallback callback) {
        try {          
            RNStripeTerminal stripeTerminal = new RNStripeTerminal(mContext);
            final String token = stripeTerminal.fetchConnectionToken();
            
            callback.onSuccess(token);
        } catch (Exception e) {
            callback.onFailure(
                new ConnectionTokenException("Failed to fetch connection token", e));
        }
    }
}
