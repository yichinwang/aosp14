/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.car.systembar;

import android.content.Context;

import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.dagger.CarSysUIDynamicOverride;
import com.android.systemui.car.privacy.CameraPrivacyElementsProviderImpl;
import com.android.systemui.car.privacy.MicPrivacyElementsProviderImpl;
import com.android.systemui.car.qc.SystemUIQCViewController;
import com.android.systemui.car.statusbar.UserNameViewController;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.ConfigurationController;

import dagger.BindsOptionalOf;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

import java.util.Optional;

import javax.inject.Provider;

/**
 * Dagger injection module for {@link CarSystemBar}.
 *
 * This module includes the non-@Inject classes used as part of the {@link CarSystemBar}, allowing
 * extensions of SystemUI to override and provide their own implementations without replacing the
 * default system bar class.
 */
@Module
public abstract class CarSystemBarModule {

    @BindsOptionalOf
    @CarSysUIDynamicOverride
    abstract ButtonSelectionStateListener optionalButtonSelectionStateListener();

    @SysUISingleton
    @Provides
    static ButtonSelectionStateListener provideButtonSelectionStateListener(@CarSysUIDynamicOverride
            Optional<ButtonSelectionStateListener> overrideButtonSelectionStateListener,
            ButtonSelectionStateController controller) {
        if (overrideButtonSelectionStateListener.isPresent()) {
            return overrideButtonSelectionStateListener.get();
        }
        return new ButtonSelectionStateListener(controller);
    }

    @BindsOptionalOf
    @CarSysUIDynamicOverride
    abstract ButtonSelectionStateController optionalButtonSelectionStateController();

    @SysUISingleton
    @Provides
    static ButtonSelectionStateController provideButtonSelectionStateController(Context context,
            @CarSysUIDynamicOverride Optional<ButtonSelectionStateController> controller) {
        if (controller.isPresent()) {
            return controller.get();
        }
        return new ButtonSelectionStateController(context);
    }

    @BindsOptionalOf
    @CarSysUIDynamicOverride
    abstract CarSystemBarController optionalCarSystemBarController();

    @SysUISingleton
    @Provides
    static CarSystemBarController provideCarSystemBarController(
            @CarSysUIDynamicOverride Optional<CarSystemBarController> carSystemBarController,
            Context context,
            UserTracker userTracker,
            CarSystemBarViewFactory carSystemBarViewFactory,
            CarServiceProvider carServiceProvider,
            BroadcastDispatcher broadcastDispatcher,
            ConfigurationController configurationController,
            ButtonSelectionStateController buttonSelectionStateController,
            Lazy<UserNameViewController> userNameViewControllerLazy,
            Lazy<MicPrivacyChipViewController> micPrivacyChipViewControllerLazy,
            Lazy<CameraPrivacyChipViewController> cameraPrivacyChipViewControllerLazy,
            ButtonRoleHolderController buttonRoleHolderController,
            SystemBarConfigs systemBarConfigs,
            Provider<SystemUIQCViewController> qcViewControllerProvider,
            Lazy<MicPrivacyElementsProviderImpl> micPrivacyElementsProvider,
            Lazy<CameraPrivacyElementsProviderImpl> cameraPrivacyElementsProvider) {
        if (carSystemBarController.isPresent()) {
            return carSystemBarController.get();
        }
        return new CarSystemBarController(context, userTracker, carSystemBarViewFactory,
                carServiceProvider, broadcastDispatcher, configurationController,
                buttonSelectionStateController, userNameViewControllerLazy,
                micPrivacyChipViewControllerLazy, cameraPrivacyChipViewControllerLazy,
                buttonRoleHolderController, systemBarConfigs, qcViewControllerProvider,
                micPrivacyElementsProvider, cameraPrivacyElementsProvider);
    }
}
