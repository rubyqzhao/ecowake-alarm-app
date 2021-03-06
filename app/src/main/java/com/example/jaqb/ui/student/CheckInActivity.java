package com.example.jaqb.ui.student;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.Toolbar;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import com.example.jaqb.IncompleteActivity;
import com.example.jaqb.MainActivity;
import com.example.jaqb.MyCoursesActivity;
import com.example.jaqb.R;
import com.example.jaqb.data.model.Course;
import com.example.jaqb.data.model.LoggedInUser;
import com.example.jaqb.services.FireBaseDBServices;
import com.example.jaqb.ui.LogoutActivity;
import com.example.jaqb.ui.instructor.AttendanceHistoryInstructorActivity;
import com.example.jaqb.ui.menu.MenuOptionsActivity;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Activity class that acts as a landing page after the user logs in. It routes the user
 * to different activities based on their action.
 * */

public class CheckInActivity extends LogoutActivity {
    private TextView upcomingClass;
    private DatabaseReference databaseReference;
    private List<Course> courseList;
    private LoggedInUser currentUser;
    private FireBaseDBServices fireBaseDBServices;

    /**
     * Triggers when the user first accesses the activity. Initializes values
     * and gets data from the firebase database.
     * @param savedInstanceState the previous state of app
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onCreate(R.layout.activity_checkin);
        upcomingClass = findViewById(R.id.upcoming_class);
        databaseReference = FirebaseDatabase.getInstance().getReference();
        fireBaseDBServices = FireBaseDBServices.getInstance();
        currentUser = fireBaseDBServices.getCurrentUser();
        courseList = currentUser.getRegisteredCourses();
        upcomingClass.setText(determineClassToDisplay());
    }

    /**
     * Triggers when activity is started
     */
    @Override
    protected void onStart() {
        super.onStart();
    }

    /**
     * Triggers when activity is resumed
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onResume() {
        super.onResume();
        upcomingClass.setText(determineClassToDisplay());
    }

    /**
     * Triggers when check in button is clicked and activate QR code scanner activity
     * for the upcoming course displayed on check in
     * @param view
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void checkinButtonOnClick(View view) {
        Double courseLongitude;
        Double courseLatitude;
        String courseQR = "";
        if(!courseList.isEmpty()) {
            Course course = currentUser.getNextCourse();
            courseLongitude = course.getLongitude();
            courseLatitude = course.getLatitude();
            courseQR = course.getCourseQRCode().split(" ")[0].trim();
            Intent intent = new Intent(this, QRCheckin.class);
            intent.putExtra("courseLongitude", courseLongitude);
            intent.putExtra("courseLatitude", courseLatitude);
            intent.putExtra("courseQR", courseQR);
            startActivity(intent);
        }
        else {
            Toast.makeText( this, "No available courses to check", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Triggers set alarm activity
     * @param view
     */
    public void setAlarmButtonOnClick(View view) {
        Intent intent = new Intent(this, IncompleteActivity.class);
        startActivity(intent);
    }

    /**
     * Triggers reward activity
     * @param view
     */
    public void seeRewardsButtonOnClick(View view) {
        for(Course c : courseList) {
            Log.d("database", c.getCode());
            calculateStats(c.getCode());
        }
        Intent intent = new Intent(this, BadgeActivity.class);
        startActivity(intent);
    }

    /**
     * Triggers Attendance History activity
     * @param view
     */
    public void seeAttendanceButtonOnClick(View view) {
        Intent intent = new Intent(this, AttendanceHistoryInstructorActivity.class);
        startActivity(intent);
    }

    /**
     * Triggers My Courses activity
     * @param view
     */
    public void myCoursesButtonOnClick(View view) {
        Intent intent = new Intent(this, MyCoursesActivity.class);
        startActivity(intent);
    }

    /**
     * Determines which class to display as next upcoming class
     * @return the list of courses or message if there is no course in list
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    protected String determineClassToDisplay() {
        String message;
        if(!courseList.isEmpty()) {
            Course course = currentUser.getNextCourse();
            String code = course.getCode();
            String days = course.getDays();
            String time = course.getTime();

            message = code + "\n" + days + " @ " + time;
        }
        else {
            message = "Course list is empty";
        }

        return message;
    }

    /**
     * Calculate and find the status of the next course
     * @param course the course to check status for
     */
    protected void calculateStats(String course) {
        final String courseName = course;

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date date = new Date();
        final String currDate = formatter.format(date);

        final Query userRef = databaseReference.child("User").child(currentUser.getuID())
                .child("attendanceHistory").child(courseName).orderByKey();
        final DatabaseReference statsRef = databaseReference.child("User").child(currentUser.getuID())
                .child("stats").child(courseName);

        // calculate and update stats
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            /**
             * Triggers when there has to a change in Database
             * @param dataSnapshot the current state of database
             */
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Map<String, Object> attendance = new HashMap<>();
                Map<String, Object> stats = new HashMap<>();
                List<String> attendDates = new ArrayList<>();

                if (dataSnapshot.exists()) {
                    for (DataSnapshot keyNode : dataSnapshot.getChildren()) {
                        attendance.put(keyNode.getKey(), keyNode.getValue());
                        attendDates.add(keyNode.getKey());
                    }
                    int currentStreak = 0;
                    int numAttended = 0;
                    int totalClasses = attendance.size();;
                    Log.d("database", attendance.toString());
                    int tempStreak = 0;
                    for(int i = 0; i < attendDates.size(); i++) {
                        if(attendance.get(attendDates.get(i)).toString().equals("true") || attendance.get(attendDates.get(i)).toString().equals("late")) {
                            tempStreak++;
                            numAttended++;
                        }
                        else {
                            if(tempStreak > currentStreak)
                                currentStreak = tempStreak;
                            tempStreak = 0;
                        }
                    }
                    if(tempStreak > currentStreak)
                        currentStreak = tempStreak;

                    stats.put("currentStreak", Integer.valueOf(currentStreak));
                    stats.put("numAttended", Integer.valueOf(numAttended));
                    stats.put("totalClasses", Integer.valueOf(totalClasses));
                }
                else {
                    stats.put("currentStreak", Integer.valueOf(0));
                    stats.put("numAttended", Integer.valueOf(0));
                    stats.put("totalClasses", Integer.valueOf(0));
                }
                Log.d("database stats", stats.toString());
                statsRef.updateChildren(stats);
            }
            /**
             * Triggers if data fails to get updated
             * @param databaseError the error due to which change could not happen
             */
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {}
        });
    }

    /**
     * Determines badge stats based on inputted attendance data
     * @param attendDates
     * @param attendance
     */
    public Map<String, Object> badgeStats(List<String> attendDates, Map<String, Object> attendance) {
        Map<String, Object> stats = new HashMap<>();
        int currentStreak = 0;
        int numAttended = 0;
        int totalClasses = attendance.size();;
        int tempStreak = 0;
        for(int i = 0; i < attendDates.size(); i++) {
            if(attendance.get(attendDates.get(i)).toString().equals("true")) {
                tempStreak++;
                numAttended++;
            }
            else {
                if(tempStreak > currentStreak)
                    currentStreak = tempStreak;
                tempStreak = 0;
            }
        }
        if(tempStreak > currentStreak)
            currentStreak = tempStreak;

        stats.put("currentStreak", Integer.valueOf(currentStreak));
        stats.put("numAttended", Integer.valueOf(numAttended));
        stats.put("totalClasses", Integer.valueOf(totalClasses));

        return stats;
    }
}