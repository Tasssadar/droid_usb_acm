package com.tassadar.usb_acm;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

@TargetApi(12)
public class SerialDeviceMgr {

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    public interface SerialDeviceMgrListener {
        void onNewDevice(SerialDevice dev);
    }

    public SerialDeviceMgr(SerialDeviceMgrListener listener, Context ctx) {
        m_listener = listener;
        m_usbManager = (UsbManager)ctx.getSystemService(Context.USB_SERVICE);

        m_permissionIntent = PendingIntent.getBroadcast(ctx, 0, new Intent(ACTION_USB_PERMISSION), 0);
    }
    
    public void registerReceiver(Context ctx) { 
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        ctx.registerReceiver(m_usbReceiver, filter);
    }

    public void unregisterReceiver(Context ctx) {
        ctx.unregisterReceiver(m_usbReceiver);
    }

    public void handleRawDevice(UsbDevice dev) {
        m_usbManager.requestPermission(dev, m_permissionIntent);
    }

    public static int matchRawDeviceToType(UsbDevice dev) {

        // Shupito v2.0
        if(dev.getVendorId() == 0x4a61 && dev.getProductId() == 0x679a)
            return SerialDevice.TYPE_CDC_ACM;

        return SerialDevice.TYPE_UNK;
    }

    private void processRawDevice(UsbDevice usbdev) {
        SerialDevice dev = SerialDevice.create(usbdev, this);
        if(dev == null)
            return;

        m_listener.onNewDevice(dev);
    }
    
    public UsbDeviceConnection openDevConnection(UsbDevice dev) {
        return m_usbManager.openDevice(dev);
    }

    private final BroadcastReceiver m_usbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device != null && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        processRawDevice(device);
                    }
                }
            }
        }
    };

    private SerialDeviceMgrListener m_listener;
    private UsbManager m_usbManager;
    private PendingIntent m_permissionIntent;
}