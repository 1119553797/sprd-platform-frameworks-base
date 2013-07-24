/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import com.android.systemui.R;

class TelephonyIcons {
    //***** Signal strength icons

    //GSM/UMTS
    static final int[][] TELEPHONY_SIGNAL_STRENGTH = {
        { R.drawable.stat_sys_signal_0,
          R.drawable.stat_sys_signal_1,
          R.drawable.stat_sys_signal_2,
          R.drawable.stat_sys_signal_3,
          R.drawable.stat_sys_signal_4 },
        { R.drawable.stat_sys_signal_0_fully,
          R.drawable.stat_sys_signal_1_fully,
          R.drawable.stat_sys_signal_2_fully,
          R.drawable.stat_sys_signal_3_fully,
          R.drawable.stat_sys_signal_4_fully }
    };

    static final int[][] TELEPHONY_SIGNAL_STRENGTH_UUI = {
        { R.drawable.stat_sys_signal_0_sprd,
          R.drawable.stat_sys_signal_1_sprd,
          R.drawable.stat_sys_signal_2_sprd,
          R.drawable.stat_sys_signal_3_sprd,
          R.drawable.stat_sys_signal_4_sprd },
        { R.drawable.stat_sys_signal_0_sprd,
          R.drawable.stat_sys_signal_1_sprd,
          R.drawable.stat_sys_signal_2_sprd,
          R.drawable.stat_sys_signal_3_sprd,
          R.drawable.stat_sys_signal_4_sprd }
    };
    static final int[][] TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR0 = {
        { R.drawable.stat_sys_signal_0_sprd,
          R.drawable.stat_sys_signal_1_1_sprd,
          R.drawable.stat_sys_signal_2_1_sprd,
          R.drawable.stat_sys_signal_3_1_sprd,
          R.drawable.stat_sys_signal_4_1_sprd },
        { R.drawable.stat_sys_signal_0_sprd,
          R.drawable.stat_sys_signal_1_1_sprd,
          R.drawable.stat_sys_signal_2_1_sprd,
          R.drawable.stat_sys_signal_3_1_sprd,
          R.drawable.stat_sys_signal_4_1_sprd }
    };
    static final int[][] TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR1 = {
        { R.drawable.stat_sys_signal_0_sprd,
            R.drawable.stat_sys_signal_1_2_sprd,
            R.drawable.stat_sys_signal_2_2_sprd,
            R.drawable.stat_sys_signal_3_2_sprd,
            R.drawable.stat_sys_signal_4_2_sprd },
          { R.drawable.stat_sys_signal_0_sprd,
            R.drawable.stat_sys_signal_1_2_sprd,
            R.drawable.stat_sys_signal_2_2_sprd,
            R.drawable.stat_sys_signal_3_2_sprd,
            R.drawable.stat_sys_signal_4_2_sprd }
      };
    static final int[][] TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR2 = {
        { R.drawable.stat_sys_signal_0_sprd,
            R.drawable.stat_sys_signal_1_3_sprd,
            R.drawable.stat_sys_signal_2_3_sprd,
            R.drawable.stat_sys_signal_3_3_sprd,
            R.drawable.stat_sys_signal_4_3_sprd },
          { R.drawable.stat_sys_signal_0_sprd,
            R.drawable.stat_sys_signal_1_3_sprd,
            R.drawable.stat_sys_signal_2_3_sprd,
            R.drawable.stat_sys_signal_3_3_sprd,
            R.drawable.stat_sys_signal_4_3_sprd }
      };
    static final int[][] TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR3 = {
        { R.drawable.stat_sys_signal_0_sprd,
            R.drawable.stat_sys_signal_1_4_sprd,
            R.drawable.stat_sys_signal_2_4_sprd,
            R.drawable.stat_sys_signal_3_4_sprd,
            R.drawable.stat_sys_signal_4_4_sprd },
          { R.drawable.stat_sys_signal_0_sprd,
            R.drawable.stat_sys_signal_1_4_sprd,
            R.drawable.stat_sys_signal_2_4_sprd,
            R.drawable.stat_sys_signal_3_4_sprd,
            R.drawable.stat_sys_signal_4_4_sprd }
      };
    static final int[][] TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR4 = {
        { R.drawable.stat_sys_signal_0_sprd,
            R.drawable.stat_sys_signal_1_5_sprd,
            R.drawable.stat_sys_signal_2_5_sprd,
            R.drawable.stat_sys_signal_3_5_sprd,
            R.drawable.stat_sys_signal_4_5_sprd },
          { R.drawable.stat_sys_signal_0_sprd,
            R.drawable.stat_sys_signal_1_5_sprd,
            R.drawable.stat_sys_signal_2_5_sprd,
            R.drawable.stat_sys_signal_3_5_sprd,
            R.drawable.stat_sys_signal_4_5_sprd }
      };
    static final int[][] TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR5 = {
        { R.drawable.stat_sys_signal_0_sprd,
            R.drawable.stat_sys_signal_1_6_sprd,
            R.drawable.stat_sys_signal_2_6_sprd,
            R.drawable.stat_sys_signal_3_6_sprd,
            R.drawable.stat_sys_signal_4_6_sprd },
          { R.drawable.stat_sys_signal_0_sprd,
            R.drawable.stat_sys_signal_1_6_sprd,
            R.drawable.stat_sys_signal_2_6_sprd,
            R.drawable.stat_sys_signal_3_6_sprd,
            R.drawable.stat_sys_signal_4_6_sprd }
      };
    static final int[][] TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR6 = {
        { R.drawable.stat_sys_signal_0_sprd,
            R.drawable.stat_sys_signal_1_7_sprd,
            R.drawable.stat_sys_signal_2_7_sprd,
            R.drawable.stat_sys_signal_3_7_sprd,
            R.drawable.stat_sys_signal_4_7_sprd },
          { R.drawable.stat_sys_signal_0_sprd,
            R.drawable.stat_sys_signal_1_7_sprd,
            R.drawable.stat_sys_signal_2_7_sprd,
            R.drawable.stat_sys_signal_3_7_sprd,
            R.drawable.stat_sys_signal_4_7_sprd }
      };
    static final int[][] TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR7 = {
        { R.drawable.stat_sys_signal_0_sprd,
            R.drawable.stat_sys_signal_1_8_sprd,
            R.drawable.stat_sys_signal_2_8_sprd,
            R.drawable.stat_sys_signal_3_8_sprd,
            R.drawable.stat_sys_signal_4_8_sprd },
          { R.drawable.stat_sys_signal_0_sprd,
            R.drawable.stat_sys_signal_1_8_sprd,
            R.drawable.stat_sys_signal_2_8_sprd,
            R.drawable.stat_sys_signal_3_8_sprd,
            R.drawable.stat_sys_signal_4_8_sprd }
      };
    static final int[][] TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR_DEFAULT = {
        { R.drawable.stat_sys_signal_0_sprd,
            R.drawable.stat_sys_signal_0_sprd,
            R.drawable.stat_sys_signal_0_sprd,
            R.drawable.stat_sys_signal_0_sprd,
            R.drawable.stat_sys_signal_0_sprd },
          { R.drawable.stat_sys_signal_0_sprd,
            R.drawable.stat_sys_signal_0_sprd,
            R.drawable.stat_sys_signal_0_sprd,
            R.drawable.stat_sys_signal_0_sprd,
            R.drawable.stat_sys_signal_0_sprd }
      };

    static final int[][] TELEPHONY_SIGNAL_STRENGTH_ROAMING_UUI = TELEPHONY_SIGNAL_STRENGTH_UUI;
    static final int[][] TELEPHONY_SIGNAL_STRENGTH_ROAMING_UUI_COLOR0 = TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR0;
    static final int[][] TELEPHONY_SIGNAL_STRENGTH_ROAMING_UUI_COLOR1 = TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR1;
    static final int[][] TELEPHONY_SIGNAL_STRENGTH_ROAMING_UUI_COLOR2 = TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR2;
    static final int[][] TELEPHONY_SIGNAL_STRENGTH_ROAMING_UUI_COLOR3 = TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR3;
    static final int[][] TELEPHONY_SIGNAL_STRENGTH_ROAMING_UUI_COLOR4 = TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR4;
    static final int[][] TELEPHONY_SIGNAL_STRENGTH_ROAMING_UUI_COLOR5 = TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR5;
    static final int[][] TELEPHONY_SIGNAL_STRENGTH_ROAMING_UUI_COLOR6 = TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR6;
    static final int[][] TELEPHONY_SIGNAL_STRENGTH_ROAMING_UUI_COLOR7 = TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR7;

    static final int[][] DATA_SIGNAL_STRENGTH_UUI_COLOR0 = TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR0;
    static final int[][] DATA_SIGNAL_STRENGTH_UUI_COLOR1 = TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR1;
    static final int[][] DATA_SIGNAL_STRENGTH_UUI_COLOR2 = TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR2;
    static final int[][] DATA_SIGNAL_STRENGTH_UUI_COLOR3 = TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR3;
    static final int[][] DATA_SIGNAL_STRENGTH_UUI_COLOR4 = TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR4;
    static final int[][] DATA_SIGNAL_STRENGTH_UUI_COLOR5 = TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR5;
    static final int[][] DATA_SIGNAL_STRENGTH_UUI_COLOR6 = TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR6;
    static final int[][] DATA_SIGNAL_STRENGTH_UUI_COLOR7 = TELEPHONY_SIGNAL_STRENGTH_UUI_COLOR7;

    static final int[][] TELEPHONY_SIGNAL_STRENGTH_ROAMING = {
        { R.drawable.stat_sys_signal_0,
          R.drawable.stat_sys_signal_1,
          R.drawable.stat_sys_signal_2,
          R.drawable.stat_sys_signal_3,
          R.drawable.stat_sys_signal_4 },
        { R.drawable.stat_sys_signal_0_fully,
          R.drawable.stat_sys_signal_1_fully,
          R.drawable.stat_sys_signal_2_fully,
          R.drawable.stat_sys_signal_3_fully,
          R.drawable.stat_sys_signal_4_fully }
    };

    static final int[][] DATA_SIGNAL_STRENGTH = TELEPHONY_SIGNAL_STRENGTH;
    static final int[][] DATA_SIGNAL_STRENGTH_UUI = TELEPHONY_SIGNAL_STRENGTH_UUI;

    //***** Data connection icons

    //GSM/UMTS
    static final int[][] DATA_G = {
            { R.drawable.stat_sys_data_connected_g,
              R.drawable.stat_sys_data_connected_g,
              R.drawable.stat_sys_data_connected_g,
              R.drawable.stat_sys_data_connected_g },
            { R.drawable.stat_sys_data_fully_connected_g,
              R.drawable.stat_sys_data_fully_connected_g,
              R.drawable.stat_sys_data_fully_connected_g,
              R.drawable.stat_sys_data_fully_connected_g }
        };

    static final int[][] DATA_3G = {
            { R.drawable.stat_sys_data_connected_3g,
              R.drawable.stat_sys_data_connected_3g,
              R.drawable.stat_sys_data_connected_3g,
              R.drawable.stat_sys_data_connected_3g },
            { R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g }
        };

    static final int[][] DATA_T = {
        { R.drawable.stat_sys_data_connected_t,
          R.drawable.stat_sys_data_connected_t,
          R.drawable.stat_sys_data_connected_t,
          R.drawable.stat_sys_data_connected_t },
        { R.drawable.stat_sys_data_fully_connected_t,
          R.drawable.stat_sys_data_fully_connected_t,
          R.drawable.stat_sys_data_fully_connected_t,
          R.drawable.stat_sys_data_fully_connected_t }
    };

    static final int[][] DATA_E = {
            { R.drawable.stat_sys_data_connected_e,
              R.drawable.stat_sys_data_connected_e,
              R.drawable.stat_sys_data_connected_e,
              R.drawable.stat_sys_data_connected_e },
            { R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e }
        };

    //3.5G
    static final int[][] DATA_H = {
            { R.drawable.stat_sys_data_connected_h,
              R.drawable.stat_sys_data_connected_h,
              R.drawable.stat_sys_data_connected_h,
              R.drawable.stat_sys_data_connected_h },
            { R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h }
    };

    //CDMA
    // Use 3G icons for EVDO data and 1x icons for 1XRTT data
    static final int[][] DATA_1X = {
            { R.drawable.stat_sys_data_connected_1x,
              R.drawable.stat_sys_data_connected_1x,
              R.drawable.stat_sys_data_connected_1x,
              R.drawable.stat_sys_data_connected_1x },
            { R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x }
            };

    // LTE and eHRPD
    static final int[][] DATA_4G = {
            { R.drawable.stat_sys_data_connected_4g,
              R.drawable.stat_sys_data_connected_4g,
              R.drawable.stat_sys_data_connected_4g,
              R.drawable.stat_sys_data_connected_4g },
            { R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g }
        };
    static final int[][] DATA_R = {
        { R.drawable.stat_sys_data_connected_roam,
          R.drawable.stat_sys_data_connected_roam,
          R.drawable.stat_sys_data_connected_roam,
          R.drawable.stat_sys_data_connected_roam },
        { R.drawable.stat_sys_data_fully_connected_roam,
          R.drawable.stat_sys_data_fully_connected_roam,
          R.drawable.stat_sys_data_fully_connected_roam,
          R.drawable.stat_sys_data_fully_connected_roam }
    };
    static final int SIM_DEFAULT_CARD_ID = 0;
    static final int[] SIM_CARD_ID = {
            R.drawable.stat_sys_card1_sprd,
            R.drawable.stat_sys_card2_sprd
    };
}

