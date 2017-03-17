package com.deew.nightswatch.live;

import android.content.Context;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by DeW on 2017/1/12.
 */

public class URLUtils {

    private static final String BIZID = "6974";
    private static final String AUTH_KEY = "bb6130658681657ea1ba9221c0262492";

    private static final String MIC_KEY ="1234";//连麦参数



    private static final char[] DIGITS_LOWER =
            {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};


    //获取大主播推流地址(广角)
    public static String getBigUrl(Context context){
        return getSafeUrl(getiImei(context)+"","b");
    }

    //获取小主播推流地址（otg）
    public static String getSmallUrl(Context context){
        return getSafeUrl(getiImei(context)+"0","s");
    }

    /*
	 * KEY+ stream_id + txTime
	 */
    private static String getSafeUrl(String imei,String BorS) {
        long txTime = getExTime();

        String input = new StringBuilder().
                append(AUTH_KEY).
                //TODO bizid + imei
                append(BIZID+"_"+ imei).
                append(Long.toHexString(txTime).toUpperCase()).toString();

        String txSecret = null;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            txSecret  = byteArrayToHexString(
                    messageDigest.digest(input.getBytes("UTF-8")));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return txSecret == null ? "" :
                new StringBuilder().
                        append("rtmp://").
                        append(BIZID).
                        append(".livepush.myqcloud.com/live/").
                        append(BIZID).
                        append("_").
                        append(imei).
                        append("?").
                        append("bizid=").
                        append(BIZID).
                        append("&").
                        append("txSecret=").
                        append(txSecret).
                        append("&").
                        append("txTime=").
                        append(Long.toHexString(txTime).toUpperCase()).
//                        append("&").
//                        append("mix=").
//                        append("layer:"+BorS+";session_id:"+MIC_KEY+";t_id:1").
                        toString();
    }

    private static String byteArrayToHexString(byte[] data) {
        char[] out = new char[data.length << 1];

        for (int i = 0, j = 0; i < data.length; i++) {
            out[j++] = DIGITS_LOWER[(0xF0 & data[i]) >>> 4];
            out[j++] = DIGITS_LOWER[0x0F & data[i]];
        }
        return new String(out);
    }

    /**
     * 统一设置到 2017/12/30 23:59:59
     */
    private static long getExTime(){
        return 1514649599;
//        return System.currentTimeMillis()/1000L + 365 * 24 * 3600L;
    }


    public static String getiImei(Context context){
//        TelephonyManager telephonyManager=(TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
//        return  telephonyManager.getDeviceId();
//        return "864221030200593";
        return "301234567891202";
    }

}
