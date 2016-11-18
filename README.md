# Silo
Realtime modular applications on Android: auto-updating, analytics, and more.

See [silo-server](https://github.com/farazfazli/silo-server/) for server implementation written in Go.

# Download

Download the *.aar bundle [here](https://my.mixtape.moe/tfioph.zip).

Bundled with [aarpack](https://github.com/farazfazli/aarpack/), a simple tool to transport Android Libraries.


# Getting Started
1. Add the Silo services (base, upstream, and downstream) and any modules you'd like to your Manifest (such as Blaise).

  ```xml
        <service
            android:name="com.farazfazli.blaise.BlaiseService" />

        <service
            android:name="com.farazfazli.silo.SiloService" />

        <service
            android:name="com.farazfazli.silo_downstream.DownstreamService" />

        <service
            android:name="com.farazfazli.silo_upstream.UpstreamService" />
  ```

2. Include Silo and any modules (such as Blaise).

  ```xml
    compile project(path: ':blaise')
    compile project(path: ':silo')
  ```
  

3. Bind reflected fragments using the "getFragments" helper method.
  ```
    public void bindFragments() {
        HashMap<String, Fragment> fragments = getFragments(new String[]{"SettingsFragment"});
        mSettingsFragment = fragments.get("SettingsFragment");
        // You can now use mSettingsFragment however you want
    }
  ```

4. Load necessary Silo modules.
  ```
    // Load each module in modules array, start Blaise to check for updates
    private void loadSiloModules() {
        String[] modulesArray = new String[]{BlaiseService.MODULE_NAME};
        Intent silo = SiloService.getIntent(this, modulesArray);
        blaise = BlaiseService.getIntent(this, true);
        startService(silo);
        registerUpdate(); // Register receiver here if you want updates to be posted to UI (optional)
        startService(blaise);
    }
  ```

5. Add local broadcast receiver, and override onStop & onDestroy. (optional)
  ```
    private void registerUpdate() {
        Log.i(TAG, "Receiver being registered");
        IntentFilter updateFilter = new IntentFilter(Constants.UPDATE_BROADCAST);
        updateReceived = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "Received broadcast, binding new views");
                bindFragments();
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(updateReceived, updateFilter);
    }

    @Override
    protected void onStop() {
        if (updateReceived != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceived);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopService(blaise);
        super.onDestroy();
    }
  ```
  

6. Capture analytics data (adding to dashboard to be developed)
  ```
    SiloService.addMessageToQueue("Loading fragment from external APK");
  ```


# Create Plugin App

1. Include the Blaise Plugin module

  ```
  compile project(path: ':blaise-plugin')
  ```

2. Extend PluginFragment

  ```
  public class SettingsFragment extends PluginFragment
  ```
3. Replace inflater to allow for reflected fragments to be loaded

  ```
      public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return resources != null ? inflater.inflate(getLayout(BuildConfig.APPLICATION_ID, "fragment_settings"), container, false) : null;
    }
  ```

# Hosting

1. Make an account at [silo.live/](https://silo.live/) or host your own instance, fill the appropriate fields

2. Update through the site, all clients will autoupdate.
  
# Motivations for Blaise auto-updating

I was motivated by low adoption of user updates and as a developer, I want each and every one of my app users to have the latest app code.

# Todo

Analytics, audience segmentation, A/B testing. I am thinking to do this in R.

# Bugs/Suggestions

Feel free to open a GitHub issue for any bugs you find, or suggestions you have.

# License

Apache

# Contact

Email me at [farazfazli@gmail.com](mailto:farazfazli@gmail.com) if you'd like to contact me. And if you're using this library, I'd love to hear from you and try out your app!
