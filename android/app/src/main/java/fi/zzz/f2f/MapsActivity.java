package fi.zzz.f2f;

import android.Manifest;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Image;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import static java.lang.System.out;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.charset.StandardCharsets.*;
import static java.security.AccessController.getContext;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private DrawerLayout mDrawerLayout;

    private static final String TAG = "F2F";

    private static final int REQUEST_PERMISSION_LOCATION = 255; // int should be between 0 and 255

    public enum Preferencies {
        COMPLETED_ONBOARDING_PREF_NAME
    };

    MapsActivity() {
        call_lock = new ReentrantLock();
        lock = new ReentrantLock();
        channels = new HashMap<String, Channel>();
        users = new HashMap<Integer, User>();
        my_channels = new HashSet<String>();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_maps);

            // Obtain the SupportMapFragment and get notified when the map is ready to be used.
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.map);
            mapFragment.getMapAsync(this);


            // Navigation bar
            mDrawerLayout = findViewById(R.id.drawer_layout);
            NavigationView navigationView = findViewById(R.id.nav_view);
            navigationView.setNavigationItemSelectedListener(
                    new NavigationView.OnNavigationItemSelectedListener() {
                        @Override
                        public boolean onNavigationItemSelected(MenuItem menuItem) {
                            // set item as selected to persist highlight
                            menuItem.setChecked(true);
                            // close drawer when item is tapped
                            mDrawerLayout.closeDrawers();
                            // Add code here to update the UI based on the item selected
                            // For example, swap UI fragments here
                            return true;
                        }
                    });


            // Set the toolbar as the action bar
            Toolbar toolbar = findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            ActionBar actionbar = getSupportActionBar();
            actionbar.setDisplayHomeAsUpEnabled(true);
            actionbar.setHomeAsUpIndicator(R.drawable.ic_menu);


            // First time user onboarding activity
            // https://developer.android.com/training/tv/playback/onboarding
            /*SharedPreferences sharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(this);
            // Check if we need to display our OnboardingFragment
            if (!sharedPreferences.getBoolean(
                    Preferencies.COMPLETED_ONBOARDING_PREF_NAME, false)) {
                // The user hasn't seen the OnboardingFragment yet, so show it
                startActivity(new Intent(this, OnboardingActivity.class));
            }*/
            Thread thread = new Thread() {
                @Override
                public void run() {
                    try {
                        addr = "192.168.1.106";
                        port = 17000;
                        Connect();
                        RegisterScript();
                        LoginScript();
                        HandleConnection();
                    }
                    catch (Exc e) {
                        Log.e(TAG, "Error: " + e.msg);
                        System.exit(1);
                    }
                }
            };
            thread.start();



            // Ask location permissions
            if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION_LOCATION);
            }
            else {
                StartLocationService();
            }


        }
        catch (java.lang.NullPointerException e) {
            Log.e(TAG, "System error");
            System.exit(1);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // We now have permission to use the location
                StartLocationService();
            }
        }
    }

    void StartLocationService() {
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // Define a listener that responds to location updates
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(final Location location) {
                // Called when a new location is found by the network location provider.
                Thread thread = new Thread() {
                    @Override
                    public void run() {
                        try {
                            SendLocation(location);
                        }
                        catch (Exc e) {
                            Log.e(TAG, "Location changing failed: " + e.msg);
                        }
                    }
                };
                thread.start();
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}

            public void onProviderEnabled(String provider) {}

            public void onProviderDisabled(String provider) {}
        };

        // Register the listener with the Location Manager to receive location updates
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Add a marker in Sydney and move the camera
        LatLng oulu_uni = new LatLng(65.05919, 25.46748);
        mMap.addMarker(new MarkerOptions().position(oulu_uni).title("Marker in Oulu University"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(oulu_uni, 16.0f));
        mMap.setIndoorEnabled(true);

    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                mDrawerLayout.openDrawer(GravityCompat.START);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    class User {
        public int user_id = -1;
        public String name = "";
        public boolean is_updated = false;
        public Date last_update;

        public HashSet<String> channels;
        public Location l;
        public long profile_image_hash = 0;
        public int age = 0;
        public boolean gender = true;
        public Bitmap profile_image;

        User() {
            channels = new HashSet<String>();
            l = new Location("");
            last_update = new Date(1970,1,1);
        }
    }

    class ChannelMessage {
        int sender_id = -1;
        String message = "", sender_name = "";
        Date received;
    }

    class Channel {
        List<ChannelMessage> messages;
        HashSet<Integer> userlist;
        int unread = 0;

        Channel() {
            messages = new ArrayList<ChannelMessage>();
            userlist = new HashSet<Integer>();
        }
        void Post(int user_id, String user_name, String msg) {
            ChannelMessage m = new ChannelMessage();
            m.received = Calendar.getInstance().getTime();
            m.message = msg;
            m.sender_id = user_id;
            m.sender_name = user_name;
            messages.add(m);
        }
    }

    class Exc extends Throwable {
        String msg;
        Exc(String s) {msg = s;}
    }

    // Client code
    private int user_id = -1;
    private String pass;
    private boolean is_registered = false;

    private HashMap<String, Channel> channels;
    private HashMap<Integer, User> users;
    private HashMap<Long, Bitmap> image_cache;
    private HashSet<String> my_channels;
    private String user_name;
    private String addr;
    private String active_channel = "";
    private Socket sock;
    private int port = 17000;
    private int age = 0;
    private boolean gender = false;
    private boolean is_logged_in = false;
    private DataInputStream input;
    private DataOutputStream output;
    private Lock call_lock, lock;

    void StoreThis() {

    }

    void LoadThis() {

    }

    void RefreshGui() {

    }

    void PostRefreshGui() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                RefreshGui();
            }
        });
    }

    void PostRefreshGuiChannel() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                RefreshGuiChannel();
            }
        });
    }

    boolean Connect() {
        if (sock == null || sock.isClosed()) {
            is_logged_in = false;

            try {
                Log.i(TAG, "Connecting " + addr + ":" + Integer.toString(port));
                sock = new Socket(addr, port);

                input = new DataInputStream(this.sock.getInputStream());
                output = new DataOutputStream (this.sock.getOutputStream());

                output.writeInt(-1);
            }
            catch (UnknownHostException e1) {
                Log.w(TAG, "Couldn't resolve host");
                return false;
            }
            catch (IOException e) {
                Log.w(TAG, "Socket IO error");
                return false;
            }
        }
        return true;
    }

    boolean RegisterScript() throws Exc {
        if (!is_registered) {
            try {
                Register();
                is_registered = true;
                StoreThis();
            }
            catch (Exc e) {
                return false;
            }
        }
        return true;
    }

    boolean LoginScript() throws Exc {
        if (!is_logged_in) {
            lock.lock();
            users.clear();
            channels.clear();
            lock.unlock();

            try {
                Login();
                RefreshChannellist();
                RefreshUserlist();
                is_logged_in = true;
                PostRefreshGui();
            }
            catch (Exc e) {
                return false;
            }
        }
        return true;
    }

    void SetName(String s) {
        if (user_name == s) return;
        try {
            if (Set("name", s))
                user_name = s;
        }
        catch (Exc e) {
            Log.e(TAG, "Changing name failed");
        }
    }

    void SetAge(int i) {
        if (age == i) return;
        try {
            if (Set("age", Integer.toString(i)))
                age = i;
        }
        catch (Exc e) {
            Log.e(TAG, "Changing age failed");
        }
    }

    void SetGender(boolean i) {
        if (gender == i) return;
        try {
            if (Set("age", Integer.toString(i ? 1 : 0)))
                gender = i;
        }
        catch (Exc e) {
            Log.e(TAG, "Changing age failed");
        }
    }

    void SetImage(Bitmap i) {
        long hash = 0;
        try {
            String hash_str = Get("profile_image_hash");
            hash = Long.parseLong(hash_str);
        }
        catch (Exc e) {
            Log.e(TAG, "Getting existing image hash failed");
        }
        while (true) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            i.compress(Bitmap.CompressFormat.JPEG, 80, out);
            String imgstr = new String(out.toByteArray());
            if (imgstr.length() > 100000) {
                int nw = i.getWidth() / 2;
                int nh = i.getHeight() / 2;
                i = Bitmap.createScaledBitmap(i, nw, nh, true);
            } else {
                if (hash != Hash(imgstr)) {
                    try {
                        Set("profile_image", imgstr);
                    }
                    catch (Exc e) {
                        Log.e(TAG, "Changing profile image failed");
                    }
                }
                break;
            }
        }
    }

    void StoreImageCache(String image_str) {
        long hash = Hash(image_str);
        String img_file = getApplicationContext().getFilesDir() + "/" + Long.toString(hash) + ".bin";
        try {
            FileOutputStream fout = new FileOutputStream(img_file);
            fout.write(image_str.getBytes(), 0, image_str.length());
            fout.close();
        }
        catch (FileNotFoundException f) {
            Log.e(TAG, "File not found: " + img_file);
        }
        catch (IOException f) {
            Log.e(TAG, "IOException: " + img_file);
        }
    }

    boolean HasCachedImage(long hash) {
        String img_file = getApplicationContext().getFilesDir() + "/" + Long.toString(hash) + ".bin";
        File f = new File(img_file);
        return f.exists();
    }

    String LoadCachedImage(long hash) {
        String img_file = getApplicationContext().getFilesDir() + "/" + Long.toString(hash) + ".bin";
        try {
            long size = new File(img_file).length();
            byte[] data = new byte[(int)size];
            FileInputStream fin = new FileInputStream(img_file);
            fin.read(data);
            return new String(data);
        }
        catch (FileNotFoundException f) {
            Log.e(TAG, "File not found: " + img_file);
        }
        catch (IOException f) {
            Log.e(TAG, "IOException: " + img_file);
        }
        return "";
    }

    void HandleConnection() {
        Log.i(TAG, "Client connection running");
        int count = 0;

        while (!Thread.interrupted()) {
            Connect();

            try {
                while (!Thread.interrupted() && sock.isConnected()) {
                    RegisterScript();
                    LoginScript();

                    Poll();
                    Sleep(1000);
                    count++;
                }

                sock.close();
            }
            catch (Exc e) {
                Log.e(TAG, "Error: " + e);
            }
            catch (IOException e) {}

            is_logged_in = false;
            sock = null;
        }

        Log.i(TAG, "Client connection stopped");
    }

    int Swap(int i) {
        return i<<24 | i>>8 & 0xff00 | i<<8 & 0xff0000 | i>>>24;
    }

    DataInputStream Call(String out_data) throws Exc {
        String in_data;

        call_lock.lock();
        try {
            while (input.available() > 0) input.readByte();

            sock.setSoTimeout(30000);
            sock.setKeepAlive(true);
            int len = out_data.length();
            output.writeInt(Swap(len));
            output.writeBytes(out_data);

            int in_size = Swap(input.readInt());
            byte[] in_buf = new byte[in_size];
            input.read(in_buf, 0, in_size);
            in_data = new String(in_buf);
        }
        catch (SocketException e) {call_lock.unlock(); throw new Exc("Call: Socket exception");}
        catch (IOException e) {call_lock.unlock(); throw new Exc("Call: IOException");}
        call_lock.unlock();

        return new DataInputStream(new ByteArrayInputStream(in_data.getBytes()));
    }

    void Sleep(int ms) {
        try {
            Thread.sleep(ms);
        }
        catch (InterruptedException e) {}
    }

    long Hash(String s) {
        return memhash(s.getBytes(), s.length());
    }

    long memhash(byte[] ptr, int count) {
        int hash = 1234567890;
        for (int i = 0; i < count; i++)
            hash = ((hash << 5) - hash) ^ ptr[i];
        return hash & 0x00000000ffffffffL;
    }

    void Register() throws Exc {
        try {
            ByteArrayOutputStream dout = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(dout);

            out.writeInt(Swap(10));

            DataInputStream in = Call(new String(dout.toByteArray()));

            user_id = Swap(in.readInt());
            byte[] pass_bytes = new byte[8];
            in.read(pass_bytes);
            pass = new String(pass_bytes);

            Log.i(TAG, "Client " + Integer.toString(user_id) + " registered (pass " + pass + ")");
        }
        catch (IOException e) {
            throw new Exc("Register: IOException");
        }
    }

    void Login() throws Exc {
        try {
            ByteArrayOutputStream dout = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(dout);

            out.writeInt(Swap(12));
            out.writeInt(Swap(user_id));
            out.writeBytes(pass);

            DataInputStream in = Call(new String(dout.toByteArray()));

            int ret = Swap(in.readInt());

            int name_len = Swap(in.readInt());
            byte[] name_bytes = new byte[name_len];
            in.read(name_bytes);
            user_name = new String(name_bytes);
            age = Swap(in.readInt());
            gender = Swap(in.readInt()) != 0 ? true : false;
            Log.i(TAG, "Client " + Integer.toString(user_id) + " logged in (" + Integer.toString(user_id) + "," + pass + ") name: " + user_name);
        }
        catch (IOException e) {
            throw new Exc("Register: IOException");
        }
    }
    boolean Set(String key, String value) throws Exc {
        try {
            ByteArrayOutputStream dout = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(dout);

            out.writeInt(Swap(30));
            out.writeInt(Swap(key.length()));
            out.writeBytes(key);
            out.writeInt(Swap(value.length()));
            out.writeBytes(value);

            DataInputStream in = Call(new String(dout.toByteArray()));

            int ret = Swap(in.readInt());
            if (ret == 1) {
                Log.e(TAG, "Client set " + key + " failed");
                return false;
            }
            Log.i(TAG, "Client set " + key);
            return true;
        }
        catch (IOException e) {
            throw new Exc("Register: IOException");
        }
    }

    String Get(String key) throws Exc {
        try {
            ByteArrayOutputStream dout = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(dout);

            out.writeInt(Swap(40));
            out.writeInt(Swap(key.length()));
            out.writeBytes(key);

            DataInputStream in = Call(new String(dout.toByteArray()));

            int value_len = Swap(in.readInt());
            byte[] value_bytes = new byte[value_len];
            in.read(value_bytes);
            String value = new String(value_bytes);

            Log.i(TAG, "Client get " + key);
            return value;
        }
        catch (IOException e) {
            throw new Exc("Register: IOException");
        }
    }

    void Join(String channel) throws Exc {
        try {
            ByteArrayOutputStream dout = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(dout);

            out.writeInt(Swap(50));
            out.writeInt(Swap(channel.length()));
            out.writeBytes(channel);

            DataInputStream in = Call(new String(dout.toByteArray()));

            int ret = Swap(in.readInt());
            if (ret == 1) {
                Log.w(TAG, "Client was already joined to channel " + channel);
                return;
            }
            else if (ret != 0) throw new Exc("Joining channel failed)");

            my_channels.add(channel);
            channels.put(channel, new Channel());
            PostRefreshGui();

            Log.i(TAG, "Client joined channel " + channel);
        }
        catch (IOException e) {
            throw new Exc("Register: IOException");
        }
    }

    void Leave(String channel) throws Exc {
        try {
            ByteArrayOutputStream dout = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(dout);

            out.writeInt(Swap(60));
            out.writeInt(Swap(channel.length()));
            out.writeBytes(channel);

            DataInputStream in = Call(new String(dout.toByteArray()));

            int ret = Swap(in.readInt());
            if (ret != 0) throw new Exc("Leaving channel failed)");

            my_channels.remove(channel);
            PostRefreshGui();

            Log.i(TAG, "Client left channel " + channel);
        }
        catch (IOException e) {
            throw new Exc("Register: IOException");
        }
    }

    void Message(int recv_user_id, String msg) throws Exc {
        if (recv_user_id < 0) return;
        try {
            ByteArrayOutputStream dout = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(dout);

            out.writeInt(Swap(70));
            out.writeInt(Swap(msg.length()));
            out.writeBytes(msg);

            DataInputStream in = Call(new String(dout.toByteArray()));

            int ret = Swap(in.readInt());
            if (ret != 0) throw new Exc("Message sending failed)");

            Log.i(TAG, "Message to " + Integer.toString(recv_user_id) + " sent: " + msg);
        }
        catch (IOException e) {
            throw new Exc("Register: IOException");
        }
    }

    class UserJoined {
        public int user_id;
        public String channel;
    }

    void Poll() throws Exc {
        List<UserJoined> join_list = new ArrayList<UserJoined>();

        try {
            ByteArrayOutputStream dout = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(dout);

            out.writeInt(Swap(80));

            DataInputStream in = Call(new String(dout.toByteArray()));

            lock.lock();

            int count = Swap(in.readInt());
            if (count < 0 || count >= 10000) {lock.unlock(); throw new Exc("Polling failed");}
            for(int i = 0; i < count; i++) {
                int sender_id = Swap(in.readInt());
                int message_len = Swap(in.readInt());
                if (message_len < 0 ||message_len > 1000000) {
                    Log.e(TAG, "Invalid message");
                    continue;
                }
                byte[] message_bytes = new byte[message_len];
                in.read(message_bytes);
                String message= new String(message_bytes);

                int j = message.indexOf(" ");
                if (j == -1) continue;
                String key = message.substring(0, j);
                message = message.substring(j + 1);

                Log.i(TAG, "Poll " + Integer.toString(i) + ": " + key);

                if (key.equals("msg")) {
                    String ch_name = "user" + Integer.toString(sender_id);
                    User u = users.get(sender_id);
                    if (!my_channels.contains(ch_name)) my_channels.add(ch_name);
                    Channel ch;
                    if (channels.containsKey(ch_name)) ch = channels.get(ch_name);
                    else ch = channels.put(ch_name, new Channel());
                    if (!ch.userlist.contains(sender_id)) ch.userlist.add(sender_id);
                    ch.Post(sender_id, u.name, message);
                    PostRefreshGui();
                }
                else if (key.equals("chmsg")) {
                    if (!users.containsKey(user_id)) continue;
                    User u = users.get(sender_id);
                    j = message.indexOf(" ");
                    String ch_name = message.substring(0, j);
                    message = message.substring(j + 1);
                    if (!channels.containsKey(ch_name)) channels.put(ch_name, new Channel());
                    Channel ch = channels.get(ch_name);
                    ch.Post(sender_id, u.name, message);
                    PostRefreshGui();
                }
                else if (key.equals("join")) {
                    String[] args = message.split(" ");
                    if (args.length != 2) continue;
                    int user_id = Integer.parseInt(args[0]);
                    String ch_name = args[1];
                    if (!users.containsKey(user_id)) users.put(user_id, new User());
                    User u = users.get(user_id);
                    u.user_id = user_id;
                    u.channels.add(ch_name);
                    if (!channels.containsKey(ch_name)) channels.put(ch_name, new Channel());
                    Channel ch = channels.get(ch_name);
                    ch.userlist.add(user_id);
                    UserJoined uj = new UserJoined();
                    uj.user_id = user_id;
                    uj.channel = ch_name;
                    join_list.add(uj);
                }
                else if (key.equals("leave")) {
                    String[] args = message.split(" ");
                    if (args.length != 2) continue;
                    int user_id = Integer.parseInt(args[0]);
                    String ch_name = args[1];
                    if (!users.containsKey(user_id)) continue;
                    User u = users.get(user_id);
                    u.channels.remove(ch_name);
                    if (!channels.containsKey(ch_name)) continue;
                    Channel ch = channels.get(ch_name);
                    ch.userlist.remove(user_id);
                    ch.Post(-1, "Server", "User " + u.name + " left channel " + ch_name);
                    if (u.channels.isEmpty())
                        users.remove(user_id);
                    PostRefreshGui();
                }
                else if (key.equals("name")) {
                    String[] args = message.split(" ");
                    if (args.length != 2) continue;
                    int user_id = Integer.parseInt(args[0]);
                    String user_name = args[1];
                    if (!users.containsKey(user_id)) continue;
                    User u = users.get(user_id);
                    u.name = user_name;
                    PostRefreshGui();
                }
                else if (key.equals("loc")) {
                    String[] args = message.split(" ");
                    if (args.length != 4) continue;
                    int user_id = Integer.parseInt(args[0]);
                    double lon = Double.parseDouble(args[1]);
                    double lat = Double.parseDouble(args[2]);
                    double elev = Double.parseDouble(args[3]);
                    if (!users.containsKey(user_id)) continue;
                    User u = users.get(user_id);
                    u.last_update = Calendar.getInstance().getTime();
                    u.l.setLongitude(lon);
                    u.l.setLatitude(lat);
                    u.l.setAltitude(elev);
                    for (String ch_name : u.channels) {
                        if (!channels.containsKey(ch_name)) channels.put(ch_name, new Channel());
                        Channel ch = channels.get(ch_name);
                        ch.Post(-1, "Server", "User " + u.name + " changed name to " + user_name);
                    }
                    PostRefreshGui();
                }
                else if (key.equals("profile")) {
                    j = message.indexOf(" ");
                    if (j == -1) continue;
                    String user_id_str = message.substring(0, j);
                    int user_id = Integer.parseInt(user_id_str);
                    message = message.substring(j+1);
                    StoreImageCache(message);
                    if (users.containsKey(user_id)) {
                        User u = users.get(user_id);
                        u.profile_image = BitmapFactory.decodeByteArray(message.getBytes(), 0, message.length());
                        u.profile_image_hash = Hash(message);
                        for (String ch_name : u.channels) {
                            if (!channels.containsKey(ch_name)) channels.put(ch_name, new Channel());
                            Channel ch = channels.get(ch_name);
                            ch.Post(-1, "Server", "User " + u.name + " updated profile image");
                        }
                    }
                    PostRefreshGui();
                }
            }
        }
        catch (IOException e) {

        }
        lock.unlock();

        if (!join_list.isEmpty()) {

            for (UserJoined uj : join_list) {

                String who = Get("who " + Integer.toString(uj.user_id));
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(who.getBytes()));
                Who(in);

                User u = users.get(uj.user_id);
                RefreshUserImage(u);

                if (!channels.containsKey(uj.channel)) continue;
                Channel ch = channels.get(uj.channel);
                ch.Post(-1, "Server", "User " + u.name + " joined channel " + uj.channel);
            }

            PostRefreshGui();
        }
    }

    void SendLocation(Location l) throws Exc {
        try {
            ByteArrayOutputStream dout = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(dout);

            out.writeInt(Swap(90));

            out.writeDouble(l.getLatitude());
            out.writeDouble(l.getLongitude());
            out.writeDouble(l.getAltitude());

            DataInputStream in = Call(new String(dout.toByteArray()));

            int ret = Swap(in.readInt());
            if (ret != 0) throw new Exc("Updating location failed)");

            Log.i(TAG, "Client updated location ");
        }
        catch (IOException e) {
            throw new Exc("SendLocation: IOException");
        }
        catch (NullPointerException e) {
            throw new Exc("SendLocation: not connected yet");
        }
    }

    void SendChannelMessage(String channel, String msg) throws Exc {
        try {
            ByteArrayOutputStream dout = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(dout);

            out.writeInt(Swap(100));

            out.writeInt(Swap(channel.length()));
            out.writeBytes(channel);
            out.writeInt(Swap(msg.length()));
            out.writeBytes(msg);

            DataInputStream in = Call(new String(dout.toByteArray()));

            int ret = Swap(in.readInt());
            if (ret != 0) throw new Exc("Message sending failed)");

            Log.i(TAG, "Client sent to channel " + channel + " message " + msg);
        }
        catch (IOException e) {
            throw new Exc("Register: IOException");
        }
    }

    void RefreshChannellist() throws Exc {
        try {
            String channellist_str = Get("channellist");
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(channellist_str.getBytes()));

            HashSet<String> ch_rem = new HashSet<String>();
            for (String ch_name : my_channels) ch_rem.add(ch_name);

            int ch_count = Swap(in.readInt());
            boolean fail = false;
            for (int i = 0; i < ch_count; i++) {
                int name_len = Swap(in.readInt());
                if (name_len <= 0) continue;
                byte[] name_bytes = new byte[name_len];
                in.read(name_bytes);
                String name = new String(name_bytes);

                if (ch_rem.contains(name)) ch_rem.remove(name);

                if (!my_channels.contains(name)) my_channels.add(name);
                if (!channels.containsKey(name)) channels.put(name, new Channel());
            }
            if (fail) throw new Exc("Getting channel-list failed");

            for (String ch : ch_rem)
                my_channels.remove(ch);

            if (!my_channels.isEmpty() && active_channel.isEmpty()) {
                SetActiveChannel(my_channels.iterator().next());
                PostRefreshGuiChannel();
            }

            Log.i(TAG, "Client updated channel-list");
        }
        catch (IOException e) {
            throw new Exc("Register: IOException");
        }
    }

    boolean Who(DataInputStream in) {
        try {
            boolean success = true;
            int user_id = Swap(in.readInt());
            int name_len = Swap(in.readInt());
            byte[] name_bytes = new byte[name_len];
            in.read(name_bytes);
            String name = new String(name_bytes);

            if (!users.containsKey(user_id)) users.put(user_id, new User());
            User u = users.get(user_id);
            u.user_id = user_id;
            u.name = name;
            u.age = Swap(in.readInt());
            u.gender = Swap(in.readInt()) != 0 ? true : false;

            u.profile_image_hash = Swap(in.readInt()) & 0x00000000ffffffffL;

            u.l.setLongitude(in.readDouble());
            u.l.setLatitude(in.readDouble());
            u.l.setAltitude(in.readDouble());

            int channel_count = Swap(in.readInt());
            if (channel_count < 0 || channel_count > 200)
                return false;
            for (int j = 0; j < channel_count; j++) {
                int ch_name_len = Swap(in.readInt());
                byte[] ch_name_bytes = new byte[ch_name_len];
                in.read(ch_name_bytes);
                String ch_name = new String(ch_name_bytes);
                if (!u.channels.contains(ch_name)) u.channels.add(ch_name);
                if (!channels.containsKey(ch_name)) channels.put(ch_name, new Channel());
                Channel ch = channels.get(ch_name);
                if (!ch.userlist.contains(user_id)) ch.userlist.add(user_id);
            }

            return true;
        }
        catch (IOException e) {
            return false;
        }
    }

    void RefreshUserlist() {
        try {
            String userlist_str = Get("userlist");
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(userlist_str.getBytes()));

            int user_count = Swap(in.readInt());
            boolean fail = false;
            for(int i = 0; i < user_count; i++) {
                fail |= !Who(in);
            }
            if (fail) throw new Exc("Getting userlist failed");

            for (User u : users.values())
                RefreshUserImage(u);

            Log.i(TAG, "Client updated userlist");
        }
        catch (Exc e) {
            Log.e(TAG, "Refreshing userlist failed: " + e.msg);
        }
        catch (IOException e) {
            Log.e(TAG, "Refreshing userlist failed: IOException");
        }
    }

    void RefreshUserImage(User u) {
        try {
            String image_str;

            if (!HasCachedImage(u.profile_image_hash)) {
                // Fetch image
                image_str = Get("image " + Long.toString(u.profile_image_hash));

                // Store to hard drive
                StoreImageCache(image_str);

            } else {
                image_str = LoadCachedImage(u.profile_image_hash);
            }

            // Load to memory
            u.profile_image = BitmapFactory.decodeByteArray(image_str.getBytes(), 0, image_str.length());
        }
        catch (Exc e) {
            Log.e(TAG, "User image refresh failed: " + u.name);
        }
    }

    void RefreshGuiChannel() {

    }

    void SetActiveChannel(String s) {

    }
}
