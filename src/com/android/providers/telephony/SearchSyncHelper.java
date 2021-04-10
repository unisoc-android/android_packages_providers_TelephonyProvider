package com.android.providers.telephony;

import android.net.Uri;
import android.R.integer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import java.util.ArrayList;
import android.content.Context;
import android.database.Cursor;
import android.os.HandlerThread;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class SearchSyncHelper {

    private Context mContext = null;
    private Handler mHandler = null;
    public static final int STATUS_ERROR = 0;
    public static final int STATUS_SUCCESS = 1;
    private static SQLiteDatabase mOpenDb = null;
    private static SearchSyncHelper mSearchHelper = null;
    public static final String TAG = "SearchSyncHelper";
    private HandlerThread mSearchHandlerThread = null;
    public static final String URI_TEMP_CONVERSATION = "content://mms-sms/temp_conversation";
    public static final int SYNC_ALL_DATA = -1;
    public static final int SYNC_TEMP_CONVERSATION_TABLE_ALL_RECORD = 1;
    public static final int SYNC_TEMP_CONVERSATION_TABLE_ADD_RECORD = 2;
    public static final int SYNC_TEMP_CONVERSATION_TABLE_DELETE_RECORD = 3;
    //add for bug 543691 begin
    public static final int SYNC_TEMP_CONVERSATION_TABLE_CREATE = 4;
    //add for bug 543691 end

    public final static String URI_CONVERSATION = "content://com.android.messaging.datamodel.MessagingContentProvider/conversation";

    public static final String TABLE_TEMP_CONVERSATION = "temp_conversation";

    private SearchSyncHelper(Context context, SQLiteDatabase db) {
        mContext = context;
        mOpenDb = db;
        initData();
    }

    public static synchronized SearchSyncHelper getInstance(Context context, SQLiteDatabase db) {
        System.out.println(TAG + "enter getInstance()");
        //bug 846531 start
        mOpenDb = db;
        //bug 846531 end
        if (null == mSearchHelper) {
            mSearchHelper = new SearchSyncHelper(context, db);
        }
        return mSearchHelper;
    }

    public static synchronized void releaseIns() {
        if (null != mSearchHelper) {
            mSearchHelper = null;
        }
    }

    private Context getContext() {
        return mContext;
    }

    private SQLiteDatabase getOpenDb() {
        return mOpenDb;
    }

    private Handler getHandler() {
        return mHandler;
    }

    private void initData() {
        System.out.println(TAG + "enter initData()");
        mSearchHandlerThread = new HandlerThread("SearchHandlerThread");
        mSearchHandlerThread.start();
        mHandler = new Handler(mSearchHandlerThread.getLooper()) {
            @Override
            public void handleMessage(android.os.Message msg) {

                switch (msg.what) {
                //add for bug 543691 begin
                case SYNC_TEMP_CONVERSATION_TABLE_CREATE:
                    CheckTable();
                //add for bug 543691 end
                    break;

                case SYNC_TEMP_CONVERSATION_TABLE_ALL_RECORD:
                    // getHandler().removeMessages(
                    // SYNC_TEMP_CONVERSATION_TABLE_ALL_RECORD);
                    syncTempTableRecord(SYNC_ALL_DATA);
                    break;
                case SYNC_TEMP_CONVERSATION_TABLE_ADD_RECORD:
                    syncTempTableRecord(((Integer) msg.obj).intValue());
                    break;
                case SYNC_TEMP_CONVERSATION_TABLE_DELETE_RECORD:
                    deleteThread(((Integer) msg.obj).intValue());
                    break;
                default:
                    break;
                }
            }
        };
    }

    public void insertRecord(int threadId) {
        /*System.out.println("the id of thread = "
                + mSearchHandlerThread.getThreadId());
        System.out.println("enter insertRecord(),threadId = " + threadId);
        // add for bug 543676 begin
        System.out.println("insert data after 4 seconds");*/
        Message msg = getHandler().obtainMessage(
                SYNC_TEMP_CONVERSATION_TABLE_ADD_RECORD, threadId);
        getHandler().sendMessageDelayed(msg, 4000);
        // add for bug 543676 end
    }

    public void deleteRecord(int threadId) {
        System.out.println("enter deleteRecord(),threadId = " + threadId);
        getHandler().obtainMessage(SYNC_TEMP_CONVERSATION_TABLE_DELETE_RECORD,
                threadId).sendToTarget();
    }

    public void syncRecord(int time) {
       // System.out.println(TAG + "enter syncRecord(), time = [" + time + "]");
        Message msg = getHandler().obtainMessage(
                SYNC_TEMP_CONVERSATION_TABLE_ALL_RECORD);
        getHandler().sendMessageDelayed(msg, time);
    }

    private int deleteThread(int threadId) {
        System.out.println(TAG + "enter deleteThread, threadId = " + threadId);
        String deleteSql = String.format(
                "delete from temp_conversation where sms_thread_id = '%d' ;",
                threadId);
        String whereClause = String.format("sms_thread_id = '%d' ;", threadId);
        System.out.println("deleteSql = " + deleteSql + "/n whereClause = "
                + whereClause);
        try {
            // getOpenHelper().getWritableDatabase().rawQuery(deleteSql, null);
            if (getOpenDb().delete(
                    "temp_conversation", whereClause, null) > 0) {
                System.out.println("delete thread = " + threadId + " success");
            } else {
                System.out.println("delete thread = " + threadId + " fail");
            }

        } catch (Exception e) {
            System.out.println("Exception occurs in deleteThread[" + threadId
                    + "], e = " + e.toString());
            return -1;
        }
        return 1;
    }
    //add for bug 543691 begin
    public void CheckTable() {
        System.out.println("====>>>>Enter Create Table ");
        //SQLiteDatabase messagingDb = getOpenHelper().getWritableDatabase();

        System.out.println("isSyncAllData, table is not exist");
        // add for bug 855055 begin
        // add for bug 543691 begin
        // add for bug 552039 begin
        String sqlCreate = "create table if not exists temp_conversation(conversation_id INTEGER,sms_thread_id INT DEFAULT(0),recipient_name TEXT,snippet_text TEXT,recipient_address TEXT,draft_snippet_text TEXT,draft_subject_text TEXT,sort_timestamp INT DEFAULT(0));";
        // add for bug 552039 end
        // add for bug 543691 end
        // add for bug 855055 end
        getOpenDb().execSQL(sqlCreate); // create an temporary table
        System.out.println("====>>>>Exit Create Table ");
    }

    private void ClearData(SQLiteDatabase messagingDb) {
        System.out.println("====>>>>Enter ClearData ");
        String sqlCreate = "delete from temp_conversation;";
        messagingDb.execSQL(sqlCreate); // create an temporary table
        System.out.println("====>>>>Exit ClearData");
    }

    private boolean isNeedSyncAll(SQLiteDatabase messagingDb) {
        if (messagingDb == null) {
            return false;
        }
        Cursor cursor = null;
        String szSQl = " Select * from temp_conversation;";
        try {
            cursor = messagingDb.rawQuery(szSQl, null);
            return (cursor == null || cursor.getCount() <= 0);
        } catch (Exception e) {
            throw e;
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }
    }
    //add for bug 543691 end
    //add for bug 552039 begin
    public void updateConvDraft(String conversationId){
        System.out.println("enter updateConvDraft, conversationId = ["+conversationId+"]");
        Cursor cursor = null;
        Cursor cursor1 = null;
        try {
            String updateDraftSql = String.format("select * from temp_conversation where conversation_id = %s",conversationId); 
            ContentResolver cr = getContext().getContentResolver();
            SQLiteDatabase db = getOpenDb();
            cursor = db.rawQuery(updateDraftSql, null);
            if (cursor == null || cursor.getCount() <= 0) {
                System.out.println("the conv data is null or count is <= 0, will syncTempTableRecord");
                syncTempTableRecord(Integer.valueOf(conversationId));
            }else {
                //will update the draft text and subject
                System.out.println("will update the draft text and subject");
                Uri convUri = Uri.parse(URI_CONVERSATION);
              //  String selection = String.format("sms_thread_id = %s", conversationId);
                String selection = String.format("_id = %d", Integer.valueOf(conversationId));
                cursor1 = cr.query(convUri, new String[] { "_id",
                        "sms_thread_id", "name", "snippet_text","draft_snippet_text" ,"draft_subject_text","sort_timestamp" }, selection,
                        null, null);
                if (cursor1 == null || cursor1.getCount() <= 0) {
                    System.out.println("the curso1r result from conversation is null or count is <= 0");
                    return;
                }
                ContentValues cv = new ContentValues();
                while (cursor1.moveToNext()) {
                    cv.put("draft_snippet_text",
                            cursor1.getString(cursor1.getColumnIndex("draft_snippet_text")));
                    cv.put("draft_subject_text",
                            cursor1.getString(cursor1.getColumnIndex("draft_subject_text")));
                    cv.put("sort_timestamp",
                            cursor1.getLong(cursor1.getColumnIndex("sort_timestamp")));
                    String whereClause = "conversation_id = ?";
                    String[] whereArgs = new String[] {conversationId};
                    if (db.update("temp_conversation", cv,
                            whereClause, whereArgs) > 0) {
                        System.out.println("update the threadId = ["
                                + conversationId + "] success");
                    }
                    System.out.println("update draft cv = " + cv.toString());
                }
            }
        } catch (Exception e) {
            System.out.println("updateConvDraft error : " + e.toString());
            e.printStackTrace();
        }finally{
            // add bug Bug 869009 start
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
            if (cursor1 != null) {
                cursor1.close();
                cursor1 = null;
            }
            // add bug Bug 869009 end
        }
    }
    //add for bug 552039 end

    public void syncTempTableRecord(int threadId) {
      //  System.out.println("enter syncTempTableRecord(), threadId = "
             //   + threadId);
        boolean isSyncAllData = false;
        Cursor cursor = null;
        Cursor cursor1 = null;
        Cursor cursor2 = null;
        Cursor cursor3 = null;
        SQLiteDatabase messagingDb = getOpenDb();//getOpenHelper().getWritableDatabase();
        isSyncAllData = isNeedSyncAll(messagingDb);

        if (threadId == SYNC_ALL_DATA) {
            isSyncAllData = true;
            //bug 844565 start
            try{
                ClearData(messagingDb);
            }catch (Exception ex){
                Log.d(TAG,"ClearData error:"+ex);
            }
            //bug 844565 end
           // System.out.println("threadId = -1, will syncAllRecord");
        }

        try {
            Uri convUri = Uri.parse(URI_CONVERSATION);
            ContentResolver cr = getContext().getContentResolver();
            // add for bug 543691 begin
            if (!isSyncAllData) {
                String selection = String
                        .format("sms_thread_id = %d", threadId);
                //add for bug 552039 begin
                // add for bug 855055 begin
                cursor = cr.query(convUri, new String[] { "_id",
                        "sms_thread_id", "name", "snippet_text","draft_snippet_text" ,"draft_subject_text","sort_timestamp" }, selection,
                        null, null);
            } else {
                cursor = cr.query(convUri, new String[] { "_id",
                        "sms_thread_id", "name", "snippet_text","draft_snippet_text" ,"draft_subject_text","sort_timestamp" }, null, null,
                        null);
                // add for bug 855055 end
                //add for bug 552039 end
            }
            // add for bug 543691 end
            // add for bug 543676 begin
            if (cursor == null || cursor.getCount() <= 0) {
              /*  System.out
                        .println("the cursor result from conversation is null or count is <= 0");*/
                return;
            }
            // add for bug 543676 end
          /*  System.out.println("query sms_thread_id,name success,the count = ["
                    + cursor.getCount() + "]");*/
            ArrayList<String> list = new ArrayList<String>();
            ContentValues cv = new ContentValues();

            //System.out.println("messagingDb.beginTransaction()");
            messagingDb.beginTransaction();
            while (cursor.moveToNext()) {
               // System.out.println("insert data to temp_DB");
                // add for bug 543691 begin
                cv.put("conversation_id",
                        cursor.getString(cursor.getColumnIndex("_id")));
                // add for bug 543691 end
                cv.put("recipient_name",
                        cursor.getString(cursor.getColumnIndex("name")));
                cv.put("sms_thread_id",
                        cursor.getInt(cursor.getColumnIndex("sms_thread_id")));
                cv.put("snippet_text",
                        cursor.getString(cursor.getColumnIndex("snippet_text")));
                // add for bug 552039 begin
                cv.put("draft_snippet_text", cursor.getString(cursor.getColumnIndex("draft_snippet_text")));
                cv.put("draft_subject_text", cursor.getString(cursor.getColumnIndex("draft_subject_text")));
                // add for bug 855055 begin
                cv.put("sort_timestamp",
                        cursor.getLong(cursor.getColumnIndex("sort_timestamp")));
                // add for bug 855055 end
                // add for bug 552039 end
                list.add(cursor.getString(cursor.getColumnIndex("name")));
                messagingDb.insert("temp_conversation", null, cv);
            }
            // add the data of recipient_address
            String sql1 = "";
            if (isSyncAllData) {
                sql1 = "select sms_thread_id from temp_conversation;";
            } else {
                sql1 = String
                        .format("select sms_thread_id from temp_conversation where sms_thread_id = %d;",
                                threadId);
               // System.out.println("just add one record threadId = ["
                  //      + threadId + "]");
            }
            // String sql2 =
            // "select recipient_ids from threads where _id = ? ;";
            // String sql3 =
            // "select address from canonical_addresses where _id = ?;";
            // String sql4 =
            // "insert into temp_conversation (recipient_address) values('?');";
            cursor1 = messagingDb.rawQuery(sql1, null);
            if (cursor1 == null) {
               // System.out.println("cursor1 is null, will rerturn");
                return;
            }
            while (cursor1.moveToNext()) {
                int thread_Id = cursor1.getInt(cursor1
                        .getColumnIndexOrThrow("sms_thread_id"));
                // System.out.println("thread_Id = " + thread_Id);
                /*Add by SPRD for bug578265  2016.7.7 Start*/
                String sql2 = "select recipient_ids from threads where _id = " + thread_Id;
                    /*String sql2 = String
                            .format("select recipient_ids from threads where _id = %d ;",
                                    thread_Id);*/
                /*Add by SPRD for bug578265  2016.7.7 End*/
                // System.out.println("sql2 = " + sql2);
                cursor2 = messagingDb.rawQuery(sql2, null);
                /*bug 1132635, start*/
                if (null == cursor2) {
                    break;
                }
                if (cursor2.getCount() <= 0) {
                    cursor2.close();
                    cursor2 = null;
                    break;
                }
                /*bug 1132635, end*/
                cursor2.moveToFirst();
                String recipient_ids = cursor2.getString(0);
                cursor2.close();
                cursor2 = null;

                String[] recipientIds = recipient_ids.split(" ");
                String recipient_address = "";
                if (recipientIds != null && recipient_ids.length() != 0) {
                    for (String item : recipientIds) {
                        String sql3 = String
                                .format("select address from canonical_addresses where _id = %s;",
                                        item);
                        // System.out.println("sql3 = " + sql3);
                        cursor3 = messagingDb.rawQuery(sql3, null);
                        /*bug 1132635, start*/
                        if (null == cursor3) {
                            break;
                        }
                        if (cursor3.getCount() <= 0) {
                            cursor3.close();
                            cursor3 = null;
                            break;
                        }
                        /*bug 1132635, end*/
                        while (cursor3.moveToNext()) {
                            String address = cursor3.getString(cursor3
                                    .getColumnIndex("address"));
                            if ("".equals(recipient_address)) {
                                recipient_address = address;
                            } else {
                                recipient_address += "," + address;
                            }
                        }
                        cursor3.close();
                        cursor3 = null;
                    }
                    // insert the recipient_address to temp_conversation
                    ContentValues values = new ContentValues();
                    //values.put("recipient_address", recipient_address);
                    // add for bug 592634 begin
                    values.put("recipient_address",recipient_address.replaceAll(" ", ""));
                    // add for 592634 end
                    String whereClause = "sms_thread_id = ?";
                    String[] whereArgs = new String[] { String
                            .valueOf(thread_Id) };
                    if (messagingDb.update("temp_conversation", values,
                            whereClause, whereArgs) > 0) {
                        //  System.out.println("update the threadId = ["
                        //    + thread_Id + "] success");
                    }
                }
            }
            if (cursor1 != null && !cursor1.isClosed()) {
                cursor1.close();
                cursor1 = null;
            }
            messagingDb.setTransactionSuccessful();
            messagingDb.endTransaction();
           /* System.out.println("temp_conversation nameList = "
                    + list.toString());*/
        } catch (Exception e) {
            System.out
                    .println("syncTempTableAllRecord error : " + e.toString());
            // add for bug 543676 begin
            e.printStackTrace();
            // add for bug 543676 end
            return;
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
            if (cursor1 != null) {
                cursor1.close();
                cursor1 = null;
            }
            if (cursor2 != null) {
                cursor2.close();
                cursor2 = null;
            }
            if (cursor3 != null) {
                cursor3.close();
                cursor3 = null;
            }
            if (messagingDb != null) {
                messagingDb = null;
            }
        }
    }

    private boolean isTableExist(String tableName) {
        boolean result = false;
        if (tableName == null) {
            return false;
        }
        Cursor cursor = null;
        try {
            SQLiteDatabase db = getOpenDb();//getOpenHelper().getWritableDatabase();
            String sql = "select * from " + tableName + " ;";
            cursor = db.rawQuery(sql, null);
            if (null == cursor) {
                return false;
            }
            while (cursor.moveToNext()) {
                int count = cursor.getInt(0);
                if (count > 0) {
                    result = true;
                }
            }
        } catch (Exception e) {
            System.out.println("isTableExist() occurs Exception : "
                    + e.toString());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

}
