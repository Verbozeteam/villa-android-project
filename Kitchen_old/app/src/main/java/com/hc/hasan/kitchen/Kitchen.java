package com.hc.hasan.kitchen;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.automation.JSONCommunicationManager;
import org.json.*;

public class Kitchen extends AppCompatActivity {
    @Override
    public void onBackPressed() {
        // nothing to do here
        // … really
    }
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(!hasFocus) {
            // Close every kind of system dialog
            Intent closeDialog = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            sendBroadcast(closeDialog);
        }
    }
    private final List blockedKeys = new ArrayList(Arrays.asList(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP));

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (blockedKeys.contains(event.getKeyCode())) {
            return true;
        } else {
            return super.dispatchKeyEvent(event);
        }
    }

    public Kitchen activity;
    public LinearLayout main_tab = null;
    private HashMap<Integer, Order> current_orders = new HashMap<>();
    private JSONCommunicationManager com_mgr;
    private String kitchen_thing_id = "";

    public void on_kitchen_update(final JSONObject kitchen_obj) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONArray orders = kitchen_obj.getJSONArray("orders");
                    for (int j = 0; j < orders.length(); j++) {
                        JSONObject order_obj = orders.getJSONObject(j);
                        if (current_orders.get(order_obj.getInt("id")) == null) {
                            JSONArray order_list = order_obj.getJSONArray("items");
                            Order.OrderItem[] ois = new Order.OrderItem[order_list.length()];
                            for (int k = 0; k < ois.length; k++) {
                                JSONObject order_item_obj = order_list.getJSONObject(k);
                                ois[k] = new Order.OrderItem(order_item_obj.getInt("id"), order_item_obj.getString("name"), order_item_obj.getInt("quantity"), order_item_obj.getInt("status"));
                            }

                            Order new_order = new Order(activity);
                            new_order.Initialize(order_obj.getInt("id"), ois, order_obj.getString("placed_by_name"), new Order.Orderer() {
                                @Override
                                public void SendMessage(int is_accept, int id) {
                                    try {
                                        JSONObject jobj = new JSONObject();
                                        jobj.put(is_accept == 1 ? "accept" : "reject", id);
                                        jobj.put("thing", kitchen_thing_id);
                                        com_mgr.addToQueue(jobj.toString());
                                    } catch (Exception e) {}
                                }
                            });
                            current_orders.put(order_obj.getInt("id"), new_order);
                            main_tab.addView(new_order);
                        }
                    }

                    // if an order is displayed but doesn't exist on the server then remove it
                    Set<Integer> keys = current_orders.keySet();
                    for (Integer i : keys) {
                        boolean found = false;
                        for (int j = 0; j < orders.length(); j++) {
                            JSONObject order_obj = orders.getJSONObject(j);
                            if (order_obj.getInt("id") == i.intValue())
                                found = true;
                        }
                        if (!found) {
                            current_orders.get(i).fade_away(new Order.Finisher() {
                                @Override
                                public void FinishOrder(Order order) {
                                    main_tab.removeView(order);
                                    try {
                                        current_orders.remove(order.order_id);
                                        main_tab.removeView(order);
                                    } catch (Exception e) {
                                    }
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                }
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View decorView = getWindow().getDecorView();
        // Hide the status bar.
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        setContentView(R.layout.activity_kitchen);

        activity = this;

        main_tab = (LinearLayout)findViewById(R.id.main_tab);

        com_mgr = JSONCommunicationManager.Create("kitchen", new JSONCommunicationManager.ServerDataCallback() {
            @Override
            public void onData(String json_str) {
                JSONObject kitchen_obj = null;
                try {
                    JSONObject obj = new JSONObject(json_str);

                    if (obj.has("rooms") && (kitchen_thing_id == null || kitchen_thing_id.equals(""))) {
                        JSONArray rooms = obj.getJSONArray("rooms");
                        for (int i = 0; i < rooms.length(); i++) {
                            JSONObject room = rooms.getJSONObject(i);
                            if (room.has("kitchen_controls")) {
                                kitchen_obj = room.getJSONArray("kitchen_controls").getJSONObject(0);
                                kitchen_thing_id = kitchen_obj.getString("id");
                                on_kitchen_update(kitchen_obj);
                            }
                        }
                    }

                    if (kitchen_thing_id != null && !kitchen_thing_id.equals("") && obj.has(kitchen_thing_id)) {
                        kitchen_obj = obj.getJSONObject(kitchen_thing_id);
                        on_kitchen_update(kitchen_obj);
                    }
                } catch (Exception e) {
                    System.out.println(e);
                }

                try {
                    if (kitchen_thing_id == null || kitchen_thing_id.equals("")) {
                        JSONObject initial_fetch = new JSONObject();
                        initial_fetch.put("code", 0); // will get things
                        com_mgr.addToQueue(initial_fetch.toString());
                    }
                } catch (Exception e) {
                    System.out.println(e);
                }
            }
        }, new JSONCommunicationManager.ServerConnectedCallback() {
            @Override
            public void onConnected() {
                kitchen_thing_id = "";
                try {
                    JSONObject initial_fetch = new JSONObject();
                    initial_fetch.put("code", 0); // will get things
                    com_mgr.addToQueue(initial_fetch.toString());
                } catch (Exception e) {
                    System.out.println(e);
                }
            }
        }, new JSONCommunicationManager.ServerDisconnectedCallback() {
            @Override
            public void onDisconnected() {
            }
        });

        com_mgr.SetServerAddress("10.10.10.10", 7990);
    }
}
