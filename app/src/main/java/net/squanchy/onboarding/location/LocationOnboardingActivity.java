package net.squanchy.onboarding.location;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.view.View;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import net.squanchy.R;
import net.squanchy.fonts.TypefaceStyleableActivity;
import net.squanchy.onboarding.Onboarding;
import net.squanchy.onboarding.OnboardingPage;
import net.squanchy.proximity.preconditions.LocationProviderPrecondition;
import net.squanchy.proximity.preconditions.ProximityOptInPersister;
import net.squanchy.proximity.preconditions.ProximityPreconditions;
import net.squanchy.service.proximity.injection.ProximityService;

import timber.log.Timber;

public class LocationOnboardingActivity extends TypefaceStyleableActivity {

    private Onboarding onboarding;
    private ProximityService service;
    private ProximityOptInPersister proximityOptInPersister;

    private ProximityPreconditions proximityPreconditions;

    private View contentRoot;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, connectionResult -> onGoogleConnectionFailed())
                .addApi(LocationServices.API)
                .build();

        LocationOnboardingComponent component = LocationOnboardingInjector.obtain(this, googleApiClient, proximityPreconditionsCallback());
        onboarding = component.onboarding();
        service = component.proximityService();
        proximityPreconditions = component.proximityPreconditions();
        proximityOptInPersister = component.proximityOptInPersister();

        googleApiClient.connect();

        setContentView(R.layout.activity_location_onboarding);
        contentRoot = findViewById(R.id.onboarding_content_root);

        findViewById(R.id.skip_button).setOnClickListener(view -> optOutFromProximity());
        findViewById(R.id.location_opt_in_button).setOnClickListener(view -> optInToProximity());

        setResult(RESULT_CANCELED);
    }

    private void optInToProximity() {
        disableUi();
        proximityOptInPersister.storeUserOptedIn();
        if (proximityPreconditions.needsActionToSatisfyPreconditions()) {
            proximityPreconditions.startSatisfyingPreconditions();
        } else {
            markPageAsSeenAndFinish();
        }
    }

    private void optOutFromProximity() {
        proximityOptInPersister.storeUserOptedOut();
        markPageAsSeenAndFinish();
    }

    private void disableUi() {
        contentRoot.setEnabled(false);
        contentRoot.setAlpha(.54f);
    }

    private void onGoogleConnectionFailed() {
        Timber.e("Google Client connection failed");
        Snackbar.make(contentRoot, R.string.onboarding_error_google_client_connection, Snackbar.LENGTH_LONG).show();
    }

    private ProximityPreconditions.Callback proximityPreconditionsCallback() {
        return new ProximityPreconditions.Callback() {

            @Override
            public void allChecksPassed() {
                startRadarAndFinish();
            }

            @Override
            public void permissionDenied() {
                showLocationError(Snackbar.make(contentRoot, R.string.onboarding_error_permission_denied, Snackbar.LENGTH_LONG));
            }

            @Override
            public void locationProviderDenied() {
                showLocationError(Snackbar.make(contentRoot, R.string.onboarding_error_location_denied, Snackbar.LENGTH_LONG));
            }

            @Override
            public void locationProviderFailed(LocationProviderPrecondition.FailureInfo failureInfo) {
                Snackbar snackbar = Snackbar.make(contentRoot, R.string.onboarding_error_location_failed, Snackbar.LENGTH_LONG)
                        .setAction(R.string.onboarding_error_location_failed_action, view -> proximityPreconditions.navigateToLocationSettings());
                showLocationError(snackbar);
            }

            @Override
            public void bluetoothDenied() {
                showLocationError(Snackbar.make(contentRoot, R.string.onboarding_error_bluetooth_denied, Snackbar.LENGTH_LONG));
            }

            @Override
            public void exceptionWhileSatisfying(Throwable throwable) {
                Timber.e(throwable, "Exception occurred while checking");
                showLocationError(Snackbar.make(contentRoot, R.string.onboarding_error_bluetooth_denied, Snackbar.LENGTH_LONG));
            }

            @Override
            public void recheckAfterActivityResult() {
                optInToProximity();
            }
        };
    }

    private void showLocationError(Snackbar snackbar) {
        enableUi();
        snackbar.show();
    }

    private void enableUi() {
        contentRoot.setEnabled(true);
        contentRoot.setAlpha(1f);
    }

    private void startRadarAndFinish() {
        service.startRadar();
        markPageAsSeenAndFinish();
    }

    void markPageAsSeenAndFinish() {
        onboarding.savePageSeen(OnboardingPage.LOCATION);
        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean handled = proximityPreconditions.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (!handled) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        boolean handled = proximityPreconditions.onActivityResult(requestCode, resultCode, data);
        if (!handled) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
