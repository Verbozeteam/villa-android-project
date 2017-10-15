package com.hc.hasan.kitchen;

import android.content.Context;
import android.support.v7.widget.LinearLayoutCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.util.ArrayList;

public class Order extends LinearLayout {
    public static class OrderItem {

        public LinearLayout container;
        public ImageButton accept, reject;
        public int id, count;
        public String order;
        public int status;

        public OrderItem(int i, String o, int c, int s) {
            id = i;
            order = o;
            count = c;
            status = s;
        }
    };
    public ArrayList<OrderItem> items;
    public int order_id;

    public interface Orderer {
        void SendMessage(int is_accepted, int id);
    }
    public interface Finisher {
        void FinishOrder(Order order);
    }

    public Order(Context context) {
        super(context);
    }

    public Order(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public Order(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void Initialize(int oid, OrderItem[] order, String orderer, Orderer _o) {
        order_id = oid;

        LinearLayout.LayoutParams p = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, 20);
        setLayoutParams(p);
        setPadding(10, 10, 10, 10);
        //setBackgroundResource(R.drawable.border);
        items = new ArrayList<>();
        setOrientation(VERTICAL);

        TextView text = new TextView(getContext());
        p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, 10);
        text.setLayoutParams(p);
        text.setText("Order from: " + orderer);
        text.setTextSize(20.0f);
        text.setTextColor(getResources().getColor(R.color.colorWhite));
        addView(text);

        final Orderer o = _o;
        final Order me = this;

        for (int i = 0; i < order.length; i++) {
            if (order[i].status == -1) // -1 means placed
                items.add(order[i]);
            p = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            p.setMargins(0, 0, 0, 10);
            order[i].container = new LinearLayout(getContext());
            order[i].container.setOrientation(HORIZONTAL);
            order[i].container.setLayoutParams(p);
            addView(order[i].container);

            //final int session = order[i].session;
            final int id = order[i].id;
            ImageButton.OnClickListener l = new ImageButton.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (int i = 0; i < items.size(); i++) {
                        if (v == (View)items.get(i).accept) {
                            o.SendMessage(1, id);
                            items.get(i).reject.setVisibility(View.GONE);
                        } else if (v == (View) items.get(i).reject) {
                            o.SendMessage(0, id);
                            //o.SendMessage("setorder:" + Integer.toString(session) + ":" + Integer.toString(id) + ":0\n");
                            items.get(i).accept.setVisibility(View.GONE);
                        } else
                            continue;
                        items.get(i).reject.setEnabled(false);
                        items.get(i).accept.setEnabled(false);
                        break;
                    }
                }
            };

            order[i].accept = new ImageButton(getContext());
            p = new LinearLayout.LayoutParams(50, 50);
            order[i].accept.setBackgroundResource(R.drawable.accepted);
            order[i].accept.setOnClickListener(l);
            p.setMargins(0, 0, 20, 0);
            order[i].accept.setLayoutParams(p);
            order[i].container.addView(order[i].accept);

            order[i].reject = new ImageButton(getContext());
            p = new LinearLayout.LayoutParams(50, 50);
            order[i].reject.setBackgroundResource(R.drawable.rejected);
            order[i].reject.setOnClickListener(l);
            p.setMargins(0, 0, 20, 0);
            order[i].reject.setLayoutParams(p);
            order[i].container.addView(order[i].reject);

            TextView itemText = new TextView(getContext());
            itemText.setText(Integer.toString(order[i].count) + "x " + order[i].order);
            itemText.setTextSize(20.0f);
            itemText.setTextColor(getResources().getColor(R.color.colorWhite));
            order[i].container.addView(itemText);

            if (order[i].status == 0 || order[i].status == 1) {
                order[i].accept.setEnabled(false);
                order[i].reject.setEnabled(false);
                if (order[i].status == 1) {
                    order[i].reject.setVisibility(View.GONE);
                }
                if (order[i].status == 0)
                    order[i].accept.setVisibility(View.GONE);
            }
        }
    }

    public void fade_away(final Finisher f) {
        AlphaAnimation anim = new AlphaAnimation(1.0f, 0.0f);
        anim.setDuration(1000);
        startAnimation(anim);
        final Order me = this;
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationEnd(Animation animation) {
                f.FinishOrder(me);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}

            @Override
            public void onAnimationStart(Animation animation) {}
        });
    }
}
