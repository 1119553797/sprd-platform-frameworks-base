/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.sim;

import android.sim.Sim;

/**
 * Central application service that provides sim management.
 */
interface ISimManager {
    Sim getSimById(int phoneId);
    Sim getSimByIccId(String iccId);
    Sim [] getSims();
    Sim [] getActiveSims();
    Sim [] getAllSims();
    String getName(int phoneId);
    void setName(int phoneId, String name);
    int getColor(int phoneId);
    int getColorIndex(int phoneId);
    void setColorIndex(int phoneId, int colorIndex);
    String getIccId(int phoneId);
}
