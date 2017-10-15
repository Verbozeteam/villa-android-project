package com.hc.hasan.homecontrol.roomfeature;

import android.view.View;
import android.view.ViewGroup;

import com.hc.hasan.homecontrol.Main;
import com.hc.hasan.homecontrol.R;
import com.hc.hasan.homecontrol.RoomControllerDevice;

/**
 * Created by hasan on 7/14/17.
 */
public class RoomFeatureBathroom extends RoomFeatureLights {

    public RoomFeatureBathroom(Main ac) {
        super(ac);
        base_switch_sending_index = 4;
        base_dimmer_sending_index = 1;
        num_lights = 2;
        num_dimmers = 1;
        switch_ids = new int[] {R.id.light1, R.id.light2};
        dimmer_ids = new int[] {R.id.dimmer3};
        dimmer_icon_ids = new int[] {R.id.dimmer_icon3};
        ltr_texts = new int[] {R.id.textView3};
        base_resource = R.layout.bathroom;
    }

    @Override
    public void onClick(RoomControllerDevice cur_device, ViewGroup main_tab) {
        main_tab.addView(view);
    }
}
