package com.napolitano.cordova.plugin.intent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.ParcelFileDescriptor;
import android.database.Cursor;
import android.content.ClipData;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.net.Uri;

import android.content.ContentResolver;
import android.webkit.MimeTypeMap;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;


public class IntentPlugin extends CordovaPlugin {

    private final String pluginName = "IntentPlugin";
    private CallbackContext onNewIntentCallbackContext = null;

    /**
     * Generic plugin command executor
     *
     * @param action
     * @param data
     * @param callbackContext
     * @return
     */
    @Override
    public boolean execute(final String action, final JSONArray data, final CallbackContext callbackContext) {
        Log.d(pluginName, pluginName + " called with options: " + data);

        Class params[] = new Class[2];
        params[0] = JSONArray.class;
        params[1] = CallbackContext.class;

        try {
            Method method = this.getClass().getDeclaredMethod(action, params);
            method.invoke(this, data, callbackContext);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return true;
    }

    /**
     * Send a JSON representation of the cordova intent back to the caller
     *
     * @param data
     * @param context
     */
    public boolean getCordovaIntent (final JSONArray data, final CallbackContext context) {
        if(data.length() != 0) {
            context.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
            return false;
        }

        Intent intent = cordova.getActivity().getIntent();
        context.sendPluginResult(new PluginResult(PluginResult.Status.OK, getIntentJson(intent)));
        return true;
    }

    /**
     * Register handler for onNewIntent event
     *
     * @param data
     * @param context
     * @return
     */
    public boolean setNewIntentHandler (final JSONArray data, final CallbackContext context) {
        if(data.length() != 1) {
            context.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
            return false;
        }

        this.onNewIntentCallbackContext = context;

        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        context.sendPluginResult(result);
        return true;
    }

    /**
     * Triggered on new intent
     *
     * @param intent
     */
    @Override
    public void onNewIntent(Intent intent) {
        if (this.onNewIntentCallbackContext != null) {

            PluginResult result = new PluginResult(PluginResult.Status.OK, getIntentJson(intent));
            result.setKeepCallback(true);
            this.onNewIntentCallbackContext.sendPluginResult(result);
        }
    }

    /**
     * Return JSON representation of intent attributes
     *
     * @param intent
     * @return
     */
    private JSONObject getIntentJson(Intent intent) {
        JSONObject intentJSON = null;
        ClipData clipData = null;
        JSONObject[] items = null;
        ContentResolver cR = this.cordova.getActivity().getApplicationContext().getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            clipData = intent.getClipData();
            if(clipData != null) {
                int clipItemCount = clipData.getItemCount();
                items = new JSONObject[clipItemCount];

                for (int i = 0; i < clipItemCount; i++) {

                    ClipData.Item item = clipData.getItemAt(i);

                    try {
                        items[i] = new JSONObject();
                        items[i].put("htmlText", item.getHtmlText());
                        items[i].put("intent", item.getIntent());
                        items[i].put("text", item.getText());
                        items[i].put("uri", item.getUri());

                        if(item.getUri() != null) {
                            String type = cR.getType(item.getUri());
                            String extension = mime.getExtensionFromMimeType(cR.getType(item.getUri()));

                            items[i].put("type", type);
                            items[i].put("extension", extension);
                        }

                    } catch (JSONException e) {
                        Log.d(pluginName, pluginName + " Error thrown during intent > JSON conversion");
                        Log.d(pluginName, e.getMessage());
                        Log.d(pluginName, Arrays.toString(e.getStackTrace()));
                    }

                }
            }
        }

        try {
            intentJSON = new JSONObject();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if(items != null) {
                    intentJSON.put("clipItems", new JSONArray(items));
                }
            }

            intentJSON.put("type", intent.getType());

            intentJSON.put("extras", toJsonObject(intent.getExtras()));
            intentJSON.put("action", intent.getAction());
            intentJSON.put("categories", intent.getCategories());
            intentJSON.put("flags", intent.getFlags());
            intentJSON.put("component", intent.getComponent());
            intentJSON.put("data", intent.getData());
            intentJSON.put("package", intent.getPackage());

            return intentJSON;
        } catch (JSONException e) {
            Log.d(pluginName, pluginName + " Error thrown during intent > JSON conversion");
            Log.d(pluginName, e.getMessage());
            Log.d(pluginName, Arrays.toString(e.getStackTrace()));

            return null;
        }
    }

    private static JSONObject toJsonObject(Bundle bundle) {
        try {
            return (JSONObject) toJsonValue(bundle);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Cannot convert bundle to JSON: " + e.getMessage(), e);
        }
    }

    private static Object toJsonValue(final Object value) throws JSONException {
        if (value == null) {
            return null;
        } else if (value instanceof Bundle) {
            final Bundle bundle = (Bundle) value;
            final JSONObject result = new JSONObject();
            for (final String key : bundle.keySet()) {
                result.put(key, toJsonValue(bundle.get(key)));
            }
            return result;
        } else if (value.getClass().isArray()) {
            final JSONArray result = new JSONArray();
            int length = Array.getLength(value);
            for (int i = 0; i < length; ++i) {
                result.put(i, toJsonValue(Array.get(value, i)));
            }
            return result;
        } else if (
                value instanceof String
                        || value instanceof Boolean
                        || value instanceof Integer
                        || value instanceof Long
                        || value instanceof Double) {
            return value;
        } else {
            return String.valueOf(value);
        }
    }

    public boolean getRealPathFromContentUrl(final JSONArray data, final CallbackContext context) {
        if(data.length() != 1) {
            //make sure there is a path to work with
            context.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
            return false;
        }

        //get The app's contentResolver
        ContentResolver cR = this.cordova.getActivity().getApplicationContext().getContentResolver();

        try {
            //create a cursor for meta data look up on the file
            Cursor cursor = cR.query(Uri.parse(data.getString(0)),  null, null, null, null);
            cursor.moveToFirst();

            //get the file's name if there is one
            int column_index = cursor.getColumnIndexOrThrow("_display_name");
            String fileName = cursor.getString(column_index);
            fileName = fileName.replaceAll("[^a-zA-Z0-9.]+", "");
            //read the file from the content://
            ParcelFileDescriptor fileParcel = cR.openFileDescriptor(Uri.parse(data.getString(0)), "r");
            InputStream fileInput = new FileInputStream(fileParcel.getFileDescriptor());

            //create a copy inside the app sandbox for use on the cordova side
            String path = this.cordova.getActivity().getApplicationContext().getFilesDir() + "/openIn/" + fileName;
            File temp = new File(path);
            OutputStream output = new FileOutputStream(temp, false);

            byte[] buffer = new byte[256 * 1024];
            int read;
            while ((read = fileInput.read(buffer)) != -1) output.write(buffer, 0, read);

            //cleanup and send the sandbox url
            fileInput.close();
            output.close();
            cursor.close();
            context.sendPluginResult(new PluginResult(PluginResult.Status.OK, temp.toString()));
        }
        catch(Exception e){
            try{
                //try again without looking up the file name
                context.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
                ParcelFileDescriptor fileParcel = cR.openFileDescriptor(Uri.parse(data.getString(0)), "r");
                InputStream fileInput = new FileInputStream(fileParcel.getFileDescriptor());
                String path = this.cordova.getActivity().getApplicationContext().getFilesDir() + "/openIn/OpenInFile";
                File temp = new File(path);
                OutputStream output = new FileOutputStream(temp, false);
                int c;
                while ((c = fileInput.read()) != -1) output.write(c);
                fileInput.close();
                output.close();
                context.sendPluginResult(new PluginResult(PluginResult.Status.OK, temp.toString()));
            }
            catch(Exception err){
                context.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
            }
        }
        return true;
    }
}