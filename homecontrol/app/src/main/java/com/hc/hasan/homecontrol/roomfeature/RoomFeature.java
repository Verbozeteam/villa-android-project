package com.hc.hasan.homecontrol.roomfeature;

import android.view.ViewGroup;

import com.hc.hasan.homecontrol.Main;
import com.hc.hasan.homecontrol.RoomControllerDevice;

/**
 * Created by hasan on 7/14/17.
 */
public abstract class RoomFeature {
    protected Main activity;

    public RoomFeature(Main ac) {
        activity = ac;
    }

    /** Called to re-initialize the view if needed */
    abstract public void reInit(String cur_lang);
    /** Called when the user clicks on this feature  */
    abstract public void onClick(RoomControllerDevice cur_device, ViewGroup main_tab);

    public void onServerData(int[] arg1) {}
    public void onServerData(int[] arg1, int[] arg2) {}
    public void onServerData(int[] arg1, int[] arg2, int[] arg3) {}
}
