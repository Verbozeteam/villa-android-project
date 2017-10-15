package com.hc.hasan.homecontrol.roomfeature;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hc.hasan.homecontrol.FoodMenuItem;
import com.hc.hasan.homecontrol.Main;
import com.hc.hasan.homecontrol.R;
import com.hc.hasan.homecontrol.RoomControllerDevice;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by hasan on 7/14/17.
 */
public class RoomFeatureKitchen extends RoomFeature {
    View view = null;
    LinearLayout orderdrawer = null;
    LinearLayout orderList = null;
    LinearLayout foodsList = null;
    Button neworder = null;
    Button placeorder = null;
    String[] menu = null;
    RoomControllerDevice cur_device;

    class OrderItem {
        public int count;
        public int food_id;
        public LinearLayout container;
        public ImageView status;
        public TextView text;

        public OrderItem(int fid, int c) {
            final OrderItem me = this;
            count = c;
            food_id = fid;
            container = new LinearLayout(activity.getApplicationContext());
            container.setOrientation(LinearLayout.HORIZONTAL);
            orderList.addView(container);

            status = new ImageView(activity.getApplicationContext());
            status.setLayoutParams(new LinearLayout.LayoutParams(35, 35));
            status.setImageResource(R.drawable.cancel);
            status.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    orderList.removeView(me.container);
                    order.remove(me);
                }
            });
            container.addView(status);

            text = new TextView(activity.getApplicationContext());
            text.setTextSize(24.0f);
            container.addView(text);

            Update();
        }

        public void Update() {
            text.setText(Integer.toString(count) + "x " + menu[food_id]);
        }
    }

    ArrayList<OrderItem> order = null;
    int orderSession = 0;

    public RoomFeatureKitchen(Main ac) {
        super(ac);
    }

    void AddToOrder(int id, int count) {
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).food_id == id) {
                order.get(i).count += count;
                order.get(i).Update();
                return;
            }
        }
        order.add(new OrderItem(id, count));
    }

    Button.OnClickListener orderListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v == neworder) {
                orderList.removeAllViews();
                order.clear();
                placeorder.setEnabled(true);
                foodsList.setEnabled(true);
                for (int i = 0; i < foodsList.getChildCount(); i++) {
                    View child = foodsList.getChildAt(i);
                    child.setEnabled(true);
                }
                placeorder.setText("Place Order");
                orderSession += 1;
            } else if (v == placeorder) {
                if (order.size() > 0) {
                    // "session:num_orders:<count>x<food_id>:...:<count>x<food_id>\n"
                    String msg = "order:" + cur_device.name + ":" + Integer.toString(orderSession) + ":" + Integer.toString(order.size());
                    for (int i = 0; i < order.size(); i++)
                        msg += ":" + order.get(i).count + "x" + order.get(i).food_id;
                    msg += "\n";
                    activity.communication_kitchen.addToQueue(msg);
                    placeorder.setEnabled(false);
                    placeorder.setText("Order Has Been Sent");
                    foodsList.setEnabled(false);
                    for (int i = 0; i < foodsList.getChildCount(); i++) {
                        FoodMenuItem child = (FoodMenuItem) foodsList.getChildAt(i);
                        child.setEnabled(false);
                    }
                    for (int i = 0; i < order.size(); i++) {
                        order.get(i).status.setImageResource(R.drawable.loading);
                        order.get(i).status.setOnClickListener(null);
                    }
                }
            }
        }
    };

    @Override
    public void onServerData(int[] orderResponse, int[] foodOption) {
        if (foodOption == null) {
            int s = (orderResponse[0] + 256) % 256;
            int i = orderResponse[1];
            int good = orderResponse[2];

            if (s == orderSession % 256) {
                if (i >= 0 && i < order.size()) {
                    if (good == 0) { // rejected
                        order.get(i).status.setImageResource(R.drawable.rejected);
                    } else {
                        order.get(i).status.setImageResource(R.drawable.accepted);
                    }
                }
            }
        } else {
            foodsList.removeAllViews();
            menu = new String[foodOption.length / 128];
            for (int j = 0; j < foodOption.length; j += 128) {
                String s = "";
                for (int i = j; i < j + 128; i++)
                    s += (char) foodOption[i];
                menu[j / 128] = s;
                FoodMenuItem it = new FoodMenuItem(activity.getApplicationContext());
                it.Initialize(s, new FoodMenuItem.Listener() {
                    @Override
                    public void OnOrder(String item, int count) {
                        for (int i = 0; i < menu.length; i++) {
                            if (menu[i].equals(item)) {
                                AddToOrder(i, count);
                                break;
                            }
                        }
                    }
                });
                foodsList.addView(it);
            }

            if (order.size() > 0) {
                foodsList.setEnabled(false);
                for (int i = 0; i < foodsList.getChildCount(); i++) {
                    FoodMenuItem child = (FoodMenuItem) foodsList.getChildAt(i);
                    child.setEnabled(false);
                }
                activity.communication_kitchen.addToQueue("whatabout:" + Integer.toString(orderSession) + "\n");
            }
        }
    }

    @Override
    public void reInit(String cur_lang) {
        if (view == null) {
            orderSession = ThreadLocalRandom.current().nextInt(0, 1000000);
            LayoutInflater inflater = LayoutInflater.from(activity);
            view = inflater.inflate(R.layout.kitchen, null);

            neworder = (Button) view.findViewById(R.id.neworder);
            neworder.setOnClickListener(orderListener);
            placeorder = (Button) view.findViewById(R.id.placeorder);
            placeorder.setOnClickListener(orderListener);

            orderdrawer = (LinearLayout) view.findViewById(R.id.orderdrawer);
            orderList = (LinearLayout) view.findViewById(R.id.currentorders);
            foodsList = (LinearLayout) view.findViewById(R.id.availablefoods);

            (view.findViewById(R.id.orderstoggle)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (orderdrawer.isShown()) {
                        orderdrawer.setVisibility(View.GONE);
                        ((Button) v).setText("Show Order");
                    } else {
                        orderdrawer.setVisibility(View.VISIBLE);
                        ((Button) v).setText("Hide Order");
                    }
                }
            });

            order = new ArrayList<OrderItem>();
        }
    }

    @Override
    public void onClick(RoomControllerDevice cd, ViewGroup main_tab) {
        cur_device = cd;
        foodsList.removeAllViews();
        main_tab.addView(view);
    }
}
