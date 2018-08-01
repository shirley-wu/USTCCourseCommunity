package com.example.dell.db;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import com.j256.ormlite.cipher.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.Dao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Created by wu-pc on 2018/5/10.
 */
public class DiaryInstrumentedTest {

    final static String TAG = "DiaryInstrumentedTest";

    private DatabaseHelper databaseHelper;
    private Dao<Diary, Integer> dao;

    private Diarybook diarybook = new Diarybook("fajskdlav");

    private String originText;
    private String updateText;

    @Before
    public void setUp() throws SQLException {
        Context appContext = InstrumentationRegistry.getTargetContext();
        databaseHelper = OpenHelperManager.getHelper(appContext, DatabaseHelper.class);
        dao = databaseHelper.getDaoAccess(Diary.class);
        diarybook.insert(databaseHelper);
    }

    @After
    public void tearDown() {
        databaseHelper.clearAll();
        OpenHelperManager.releaseHelper();
    }

    @Test
    public void testGetDao() {
        assertEquals("Diary", dao.getTableName());
    }

    @Test
    public void testSaveAndGetDiary() throws SQLException {
        // 测试Diary的基本操作中各个表项的变与不变

        originText = "Testing testing do not repeat testing testing 221341151" + (new Date()).getTime() + (new Random()).nextDouble();
        updateText = "hlelleelelfjakdl;jag alknals" + (new Date()).getTime() + (new Random()).nextDouble();

        Diary diary = new Diary();
        List<Diary> diaryList;
        diary.setText(originText);
        diary.setHtmlText("<p>" + originText + "</p>");
        diary.setDate();
        diary.setDiarybook(diarybook);
        diary.setIsLike(true);

        // create
        diary.insert(databaseHelper);
        Log.d(TAG, "testSaveAndGetDiary: modified when inserting = " + diary.getModified());

        // query
        diaryList = dao.queryBuilder().where().eq("text", originText).query();
        assertEquals(1, diaryList.size()); // TODO: not safe: assumes that there is no such text by wxq
        assertEquals(diary.getDate(), diaryList.get(0).getDate());
        assertEquals(originText, diaryList.get(0).getText());
        assertEquals("<p>" + originText + "</p>", diaryList.get(0).getHtmlText());
        assertEquals(true, diaryList.get(0).getIsLike());
        assertEquals(0, diaryList.get(0).getStatus());

        // update
        diary.setText(updateText);
        diary.update(databaseHelper);
        diaryList = dao.queryBuilder().where().eq("text", originText).query();
        assertEquals(0, diaryList.size()); // TODO: not safe: assumes that there is no such text by wxq
        diaryList = dao.queryBuilder().where().eq("text", updateText).query();
        assertEquals(1, diaryList.size()); // TODO: not safe: assumes that there is no such text by wxq
        assertEquals(diary.getDate(), diaryList.get(0).getDate());
        assertEquals(0, diaryList.get(0).getStatus());
        Log.d(TAG, "testSaveAndGetDiary: modified when updating 1 = " + diary.getModified());

        // update after so-called sync
        diary.setStatus(9);
        diary.update(databaseHelper);
        diaryList = dao.queryBuilder().where().eq("text", updateText).query();
        assertEquals(1, diaryList.size()); // TODO: not safe: assumes that there is no such text by wxq
        assertEquals(diary.getDate(), diaryList.get(0).getDate());
        assertEquals(1, diaryList.get(0).getStatus());
        Log.d(TAG, "testSaveAndGetDiary: modified when updating 2 = " + diary.getModified());

        // delete
        diary.delete(databaseHelper);
        diaryList = dao.queryBuilder().where().eq("text", updateText).query();
        assertEquals(1, diaryList.size()); // TODO: not safe: assumes that there is no such text by wxq
        assertEquals(-1, diaryList.get(0).getStatus());
        Log.d(TAG, "testSaveAndGetDiary: modified when deleting = " + diary.getModified());
    }

    @Test
    public void testGetAllDiary() throws SQLException {
        List<Diary> diaryList = Diary.getAll(databaseHelper, true);
        assertTrue(diaryList.size() >= 0);
    }

    @Test
    public void testGetAllDescendingDiary() throws SQLException {
        String text = "akkljdvfavba";

        Diary diary = new Diary(text);
        diary.setDate(new Date(4000, 1, 1));
        diary.setDiarybook(diarybook);
        diary.insert(databaseHelper);

        List<Diary> diaryList = Diary.getAll(databaseHelper, false);
        assertTrue(diaryList.size() > 0);
        assertEquals(diary.getDate(), diaryList.get(0).getDate());

        diary.delete(databaseHelper);
    }

    @Test
    public void testGetAllAscendingDiary() throws SQLException {
        String text = "akkljdvfavba";

        Diary diary = new Diary(text);
        diary.setDate(new Date(1000, 1, 1));
        diary.setDiarybook(diarybook);
        diary.insert(databaseHelper);

        List<Diary> diaryList = Diary.getAll(databaseHelper, true);
        assertTrue(diaryList.size() > 0);
        assertEquals(diaryList.get(0).getDate(), diary.getDate());

        diary.delete(databaseHelper);
    }

}