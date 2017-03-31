package net.squanchy.onboarding.location;

import android.app.Activity;

import net.squanchy.injection.ActivityContextModule;
import net.squanchy.injection.ApplicationInjector;

final class LocationOnboardingInjector {

    private LocationOnboardingInjector() {
        // no instances
    }

    public static LocationOnboardingComponent obtain(Activity activity) {
        return DaggerLocationOnboardingComponent.builder()
                .activityContextModule(new ActivityContextModule(activity))
                .applicationComponent(ApplicationInjector.obtain(activity))
                .build();
    }
}