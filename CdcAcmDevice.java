// Largely inspired by http://code.google.com/p/usb-serial-for-android/

package com.tassadar.usb_acm;

import java.io.IOException;

import android.annotation.TargetApi;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;

@TargetApi(12)
public class CdcAcmDevice extends SerialDevice {

    private static final int DEFAULT_BAUDRATE = 115200;
    private static final int DEFAULT_STOPBITS = 0;
    private static final int DEFAULT_PARITY = 0;
    private static final int DEFAULT_DATABITS = 8;

    private static final int SET_LINE_CODING = 0x20;
    private static final int USB_RECIP_INTERFACE = 0x01;
    private static final int USB_RT_ACM = UsbConstants.USB_TYPE_CLASS | USB_RECIP_INTERFACE;

    public CdcAcmDevice(UsbDevice dev, UsbDeviceConnection conn) {
        super(dev, conn);
    }

    public int getType() { return SerialDevice.TYPE_CDC_ACM; }

    public void open() throws IOException {
        if(m_dev.getInterfaceCount() < 2)
            throw new IOException("Couldn't open CDC ACM device: interface count is too low");

        m_ctrlInterface = m_dev.getInterface(0);
        if(m_ctrlInterface.getInterfaceClass() != UsbConstants.USB_CLASS_COMM)
            throw new IOException("Couldn't open CDC ACM device: wrong ctrl interface class");
        
        m_dataInterface = m_dev.getInterface(1);
        if(m_dataInterface.getInterfaceClass() != UsbConstants.USB_CLASS_CDC_DATA)
            throw new IOException("Couldn't open CDC ACM device: wrong data interface class");
        
        if(!m_conn.claimInterface(m_ctrlInterface, true))
            throw new IOException("Couldn't open CDC ACM device: failed to claim ctrl interface");
        
        if(!m_conn.claimInterface(m_dataInterface, true)) {
            m_conn.releaseInterface(m_ctrlInterface);
            throw new IOException("Couldn't open CDC ACM device: failed to claim data interface");
        }

        m_ctrlEndpoint = m_ctrlInterface.getEndpoint(0);

        m_writeEndpoint = m_dataInterface.getEndpoint(0);
        m_readEndpoint = m_dataInterface.getEndpoint(1);
        
        m_baudrate = DEFAULT_BAUDRATE;
        m_stopBits = DEFAULT_STOPBITS;
        m_parity = DEFAULT_PARITY;
        m_dataBits = DEFAULT_DATABITS;

        sendLineCoding();
    }

    private void sendLineCoding() {
        
        byte[] msg = { 
                (byte)(m_baudrate & 0xFF),
                (byte)((m_baudrate >> 8) & 0xFF),
                (byte)((m_baudrate >> 16) & 0xFF),
                (byte)((m_baudrate >> 24) & 0xFF),
                
                (byte)m_stopBits,
                (byte)m_parity,
                (byte)m_dataBits
        };

        sendAcmCtrlMsg(SET_LINE_CODING, 0, msg);
    }

    private int sendAcmCtrlMsg(int request, int value, byte[] buf) {
        return m_conn.controlTransfer(USB_RT_ACM, request, value, 0, buf, buf != null ? buf.length : 0, 5000);
    }

    public int read(byte[] dest, int timeoutMs) throws IOException {
        final int read;
        synchronized (m_readLock) {
            int readAmt = Math.min(dest.length, m_readBuff.length);
            read = m_conn.bulkTransfer(m_readEndpoint, m_readBuff, readAmt, timeoutMs);
            if (read < 0) {
                // This sucks: we get -1 on timeout, not 0 as preferred.
                // We *should* use UsbRequest, except it has a bug/api oversight
                // where there is no way to determine the number of bytes read
                // in response :\ -- http://b.android.com/28023
                return 0;
            }
            System.arraycopy(m_readBuff, 0, dest, 0, read);
        }
        return read;
    }

    public int write(byte[] src, int timeoutMs) throws IOException {
        int offset = 0;

        while (offset < src.length) {
            final int writeLength;
            final int written;

            synchronized (m_writeLock) {
                final byte[] writeBuffer;

                writeLength = Math.min(src.length - offset, m_writeBuff.length);
                if (offset == 0) {
                    writeBuffer = src;
                } else {
                    // bulkTransfer does not support offsets, make a copy.
                    System.arraycopy(src, offset, m_writeBuff, 0, writeLength);
                    writeBuffer = m_writeBuff;
                }

                written = m_conn.bulkTransfer(m_writeEndpoint, writeBuffer, writeLength, timeoutMs);
            }
            if (written <= 0) {
                throw new IOException("Error writing " + writeLength
                        + " bytes at offset " + offset + " length=" + src.length);
            }

            offset += written;
        }
        return offset;
    }

    public void setBaudRate(int baudrate) {
        m_baudrate = baudrate;
        sendLineCoding();
    }

    public void setStopBits(int stopbits) {
        m_stopBits = stopbits;
        sendLineCoding();
    }

    public void setParity(int parity) {
        m_parity = parity;
        sendLineCoding();
    }

    public void setDataBits(int databits) {
        m_dataBits = databits;
        sendLineCoding();
    }

    private UsbInterface m_ctrlInterface;
    private UsbInterface m_dataInterface;
    
    private UsbEndpoint m_ctrlEndpoint;
    private UsbEndpoint m_readEndpoint;
    private UsbEndpoint m_writeEndpoint;
}