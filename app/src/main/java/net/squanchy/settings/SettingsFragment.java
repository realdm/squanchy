package net.squanchy.settings;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.support.design.widget.Snackbar;
import android.widget.ListView;

import com.google.firebase.auth.FirebaseUser;

import net.squanchy.BuildConfig;
import net.squanchy.R;
import net.squanchy.navigation.Navigator;
import net.squanchy.remoteconfig.RemoteConfig;
import net.squanchy.signin.SignInService;
import net.squanchy.support.lang.Optional;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public class SettingsFragment extends PreferenceFragment {

    private final CompositeDisposable subscriptions = new CompositeDisposable();

    private SignInService signInService;
    private RemoteConfig remoteConfig;
    private Navigator navigator;

    private PreferenceCategory accountCategory;
    private Preference accountEmailPreference;
    private Preference accountSignInSignOutPreference;

    private PreferenceCategory settingsCategory;
    private Preference proximityOptInPreference;
    private Preference contestStandingsPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.settings_preferences);
        getPreferenceScreen().setOrderingAsAdded(false);

        displayBuildVersion();

        if (!BuildConfig.DEBUG) {
            removeDebugCategory();
        }

        SettingsComponent component = SettingsInjector.obtain(getActivity());
        signInService = component.signInService();
        remoteConfig = component.remoteConfig();
        navigator = component.navigator();

        accountCategory = (PreferenceCategory) findPreference(getString(R.string.account_category_key));
        accountEmailPreference = findPreference(getString(R.string.account_email_preference_key));
        accountCategory.removePreference(accountEmailPreference);
        accountSignInSignOutPreference = findPreference(getString(R.string.account_signin_signout_preference_key));

        settingsCategory = (PreferenceCategory) findPreference(getString(R.string.settings_category_key));
        proximityOptInPreference = findPreference(getString(R.string.location_preference_key));
        contestStandingsPreference = findPreference(getString(R.string.contest_standings_preference_key));
        contestStandingsPreference.setOnPreferenceClickListener(preference -> {
            navigator.toContest();
            return true;
        });

        Preference aboutPreference = findPreference(getString(R.string.about_preference_key));
        aboutPreference.setOnPreferenceClickListener(preference -> {
            navigator.toAboutSquanchy();
            return true;
        });
    }

    private void displayBuildVersion() {
        String buildVersionKey = getString(R.string.build_version_preference_key);
        Preference buildVersionPreference = findPreference(buildVersionKey);
        String buildVersion = String.format(getString(R.string.version_x), BuildConfig.VERSION_NAME);
        buildVersionPreference.setTitle(buildVersion);
    }

    private void removeDebugCategory() {
        String debugCategoryKey = getString(R.string.debug_category_preference_key);
        Preference debugCategory = findPreference(debugCategoryKey);
        getPreferenceScreen().removePreference(debugCategory);
    }

    @Override
    public void onStart() {
        super.onStart();

        hideDividers();

        hideProximityAndContestBasedOnRemoteConfig();

        subscriptions.add(
                signInService.currentUser()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::onUserChanged)
        );
    }

    private void hideDividers() {
        ListView list = (ListView) getView().findViewById(android.R.id.list);
        list.setDivider(null);
        list.setDividerHeight(0);
    }

    private void hideProximityAndContestBasedOnRemoteConfig() {
        subscriptions.add(
                remoteConfig.proximityServicesEnabled()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::setProximityAndContestUiStatus)
        );
    }

    private void setProximityAndContestUiStatus(boolean enabled) {
        if (enabled) {
            showIfNotAlreadyShown(proximityOptInPreference);
            showIfNotAlreadyShown(contestStandingsPreference);
            proximityOptInPreference.setSelectable(true);
        } else {
            settingsCategory.removePreference(proximityOptInPreference);
            settingsCategory.removePreference(contestStandingsPreference);
        }
    }

    private void showIfNotAlreadyShown(Preference preference) {
        if (settingsCategory.findPreference(preference.getKey()) == null) {
            settingsCategory.addPreference(preference);
        }
    }

    private void onUserChanged(Optional<FirebaseUser> user) {
        if (user.isPresent() && !user.get().isAnonymous()) {
            onSignedInWith(user.get());
        } else {
            onSignedOut();
        }
    }

    private void onSignedInWith(FirebaseUser firebaseUser) {
        accountCategory.addPreference(accountEmailPreference);
        accountEmailPreference.setTitle(firebaseUser.getEmail());

        accountSignInSignOutPreference.setTitle(R.string.sign_out_title);
        accountSignInSignOutPreference.setOnPreferenceClickListener(
                preference -> {
                    signInService.signOut()
                            .subscribe(() ->
                                    Snackbar.make(getView(), R.string.settings_message_signed_out, Snackbar.LENGTH_SHORT).show()
                            );
                    return true;
                }
        );
    }

    private void onSignedOut() {
        accountCategory.removePreference(accountEmailPreference);

        accountSignInSignOutPreference.setTitle(R.string.sign_in_title);
        accountSignInSignOutPreference.setOnPreferenceClickListener(
                preference -> {
                    navigator.toSignIn();
                    return true;
                }
        );
    }

    @Override
    public void onStop() {
        super.onStop();
        subscriptions.clear();
    }
}
