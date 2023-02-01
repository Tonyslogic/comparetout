package com.tfcode.comparetout.model;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {PricePlan.class, DayRate.class}, version = 1)
@TypeConverters({Converters.class})
public abstract class ToutcDB extends RoomDatabase {

    public abstract PricePlanDAO pricePlanDAO();

    private static volatile ToutcDB INSTANCE;
    private static final int NUMBER_OF_THREADS = 8;
    static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    static ToutcDB getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (ToutcDB.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    ToutcDB.class, "toutc_database").setQueryExecutor(databaseWriteExecutor)
                            .build();
//                            .addCallback(sRoomDatabaseCallback).build();
                }
            }
        }
        return INSTANCE;
    }

//    private static final ToutcDB.Callback sRoomDatabaseCallback = new RoomDatabase.Callback() {
//        @Override
//        public void onCreate(@NonNull SupportSQLiteDatabase db) {
//            super.onCreate(db);
//
//            // If you want to keep data through app restarts,
//            // comment out the following block
//            databaseWriteExecutor.execute(() -> {
//                // Populate the database in the background.
//                // If you want to start with more plans, just add them.
//                PricePlanDAO dao = INSTANCE.pricePlanDAO();
//                dao.deleteAll();
//
//                System.out.println("creating an entry in the DB");
//                DayRate dr = new DayRate();
//                List<Integer> dayList = Arrays.asList(0,1,2,3,4,5,6);
//                IntHolder intHolder = new IntHolder();
//                intHolder.ints = dayList;
//                dr.setDays(intHolder);
//
//                List<Double> hours = Arrays.asList(17.47, 17.47, 17.47, 17.47, 17.47, 17.47,
//                        17.47, 17.47, 23.77, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 29.04, 29.04, 23.77,
//                        23.77, 23.77, 23.77, 17.47, 17.47);
//                DoubleHolder doubleHolder = new DoubleHolder();
//                doubleHolder.doubles = hours;
//                dr.setHours(doubleHolder);
//                dr.setStartDate("01/01");
//                dr.setEndDate("12/31");
//
//                List<DayRate> drs = new ArrayList<>();
//                drs.add(dr);
//
//                PricePlan p = new PricePlan();
//                p.setSupplier("Energia");
//                p.setPlanName("SmartEV");
//                p.setFeed(21);
//                p.setReference("TODO_ref");
//                p.setLastUpdate("TODO_DATE");
//                p.setActive(true);
//                p.setStandingCharges(255.22);
//                p.setSignUpBonus(0);
//                dao.addNewPricePlanWithDayRates(p, drs);
//
//                PricePlan p1 = new PricePlan();
//                p1.setSupplier("Energia");
//                p1.setPlanName("SmartEV D/N");
//                p1.setFeed(21);
//                p1.setReference("TODO_ref");
//                p1.setLastUpdate("TODO_DATE");
//                p1.setActive(true);
//                p1.setStandingCharges(255.22);
//                p1.setSignUpBonus(0);
//                dao.addNewPricePlanWithDayRates(p1, drs);
//            });
//        }
//    };
}
