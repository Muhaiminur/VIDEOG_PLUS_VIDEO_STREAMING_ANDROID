package com.gtechnologies.videogplus.Adapter;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gtechnologies.videogplus.Activity.BrowserActivity;
import com.gtechnologies.videogplus.Activity.Playlist;
import com.gtechnologies.videogplus.Http.ContentApiClient;
import com.gtechnologies.videogplus.Http.ContentApiInterface;
import com.gtechnologies.videogplus.Http.GhooriApiClient;
import com.gtechnologies.videogplus.Http.GhooriApiInterface;
import com.gtechnologies.videogplus.Interface.SuggesationInterface;
import com.gtechnologies.videogplus.Library.ExoPlayerVideoHandler;
import com.gtechnologies.videogplus.Library.KeyWord;
import com.gtechnologies.videogplus.Library.Utility;
import com.gtechnologies.videogplus.Model.Bkash;
import com.gtechnologies.videogplus.Model.Content;
import com.gtechnologies.videogplus.Model.Subscription;
import com.gtechnologies.videogplus.R;
import com.squareup.picasso.Picasso;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Created by Hp on 4/10/2018.
 */

public class SuggestionAdapter extends BaseAdapter {

    Context context;
    List<Content> contentList;
    Utility utility;
    SuggesationInterface suggesationInterface;
    GhooriApiInterface apiInterface = GhooriApiClient.getBaseClient().create(GhooriApiInterface.class);
    Bkash bkash;

    public SuggestionAdapter(Context context, List<Content> contents, SuggesationInterface suggesationInterface){
        this.context = context;
        this.contentList = contents;
        utility = new Utility(this.context);
        this.suggesationInterface = suggesationInterface;
        bkash = utility.getBkashSubscription();
    }

    @Override
    public int getCount() {
        return contentList.size();
    }

    @Override
    public Object getItem(int position) {
        return contentList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        if(convertView==null){
            LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.suggestion_layout, null);
        }
        LinearLayout suggesationLayout = (LinearLayout)convertView.findViewById(R.id.suggestion_layout);
        ImageView image = (ImageView)convertView.findViewById(R.id.image);
        ImageView premium = (ImageView)convertView.findViewById(R.id.premium);
        TextView title = (TextView)convertView.findViewById(R.id.title);
        TextView description = (TextView)convertView.findViewById(R.id.description);
        TextView duration = (TextView)convertView.findViewById(R.id.duration);
        utility.setFonts(new View[]{title, description, duration});
        if(contentList.get(position).getPremium().equals("yes")) {
            premium.setVisibility(View.VISIBLE);
        }
        else{
            premium.setVisibility(View.GONE);
        }
        Picasso.with(context)
                .load(context.getString(R.string.image_url)+ contentList.get(position).getImage())
                .error(R.drawable.rg)
                .placeholder(R.drawable.rg)
                .into(image);
        title.setText(utility.getLangauge().equals("bn")?
                utility.decodeBase64(contentList.get(position).getTitleBn())
                :contentList.get(position).getTitle());
        description.setText(utility.getLangauge().equals("bn")?
                utility.decodeBase64(contentList.get(position).getBriefBn())
                :contentList.get(position).getBrief());
        duration.setText(utility.getLangauge().equals("bn")?
                utility.convertToBangle(utility.convertSecondsToHour(contentList.get(position).getDuration()))
                :utility.convertSecondsToHour(contentList.get(position).getDuration()));
        suggesationLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bkash = utility.getBkashSubscription();
                if (utility.getBkashSubscription().getMsisdn().length() == 0) {
                    utility.makeSubscriptionDialog(false);
                } else {
                    if(utility.isNetworkAvailable()) {
                        viewBkashStatus(position);
                    }
                    else{
                        if(System.currentTimeMillis()<Long.parseLong(utility.getBkashSubscription().getExpireTime())){
                            suggesationInterface.refreshPlayer(position);
                        }
                        else{
                            utility.showToast("You are not subscribed");
                        }
                    }
                }
                /*if (contentList.get(position).getPremium().equals("yes")) {
                    if(utility.getMdn().equals("00")){
                        makeSubscriptionDialog(contentList.get(position).getId(),position);
                    }
                    else{
                        checkMasterSubscription(contentList.get(position).getId(),position);
                    }
                }
                else {
                    suggesationInterface.refreshPlayer(position);
                }*/
            }
        });
        return convertView;
    }

    private void viewBkashStatus(final int position){
        try {
            utility.showProgress(true);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("mdn", bkash.getMsisdn());
            MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
            RequestBody requestBody = RequestBody.create(mediaType, jsonObject.toString());
            final Call<ResponseBody> call = apiInterface.viewBkashStatus(context.getString(R.string.bkash_authorization_key), requestBody);
            call.enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    utility.hideProgress();
                    if (response.isSuccessful() && response.code() == 200) {
                        try {
                            JSONObject object = new JSONObject(response.body().string());
                            Bkash b = new Bkash();
                            b.setMsisdn(bkash.getMsisdn());
                            b.setStatus(object.optString("status"));
                            b.setUrl(object.optString("url"));
                            b.setExpireTime(object.optString("expireTime"));
                            utility.setBkashSubscription(b);
                            if (b.getStatus().equals("subscribed")) {
                                suggesationInterface.refreshPlayer(position);
                            } else {
                                activeBkash();
                            }
                        }
                        catch (Exception ex){
                            utility.logger(ex.toString());
                        }
                    } else {
                        utility.showToast(String.valueOf(response.code()));
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    utility.hideProgress();
                    //initiateView();
                }
            });
        }
        catch (Exception ex){
            utility.logger(ex.toString());
        }
    }

    private void activeBkash(){
        try {
            utility.showProgress(true);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("mdn", bkash.getMsisdn());
            MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
            RequestBody requestBody = RequestBody.create(mediaType, jsonObject.toString());
            Call<ResponseBody> call = apiInterface.activateBkash(context.getString(R.string.bkash_authorization_key), requestBody);
            call.enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    utility.hideProgress();
                    if (response.isSuccessful() && response.code() == 200) {
                        try {
                            JSONObject object = new JSONObject(response.body().string());
                            Bkash b = new Bkash();
                            b.setMsisdn(bkash.getMsisdn());
                            b.setStatus(object.optString("status"));
                            b.setUrl(object.optString("url"));
                            b.setExpireTime(object.optString("expireTime"));
                            utility.setBkashSubscription(b);
                            Intent intent = new Intent(context, BrowserActivity.class);
                            intent.putExtra("url", b.getUrl());
                            context.startActivity(intent);
                            //initiateView();
                        }
                        catch (Exception ex){
                            utility.logger(ex.toString());
                        }
                    } else {
                        utility.showToast(String.valueOf(response.code()));
                        //initiateView();
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    utility.hideProgress();
                    //initiateView();
                }
            });
        }
        catch (Exception ex){
            utility.logger(ex.toString());
        }
    }

/*    public void makeSubscriptionDialog(final int trackId, final int position) {
        try {
            final Dialog dialog = new Dialog(context);
            HashMap<String, Integer> screenRes = utility.getScreenRes();
            int width = screenRes.get(KeyWord.SCREEN_WIDTH);
            int height = screenRes.get(KeyWord.SCREEN_HEIGHT);
            int mywidth = (width / 10) * 8;
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(R.layout.number_layout);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            LinearLayout numberLayout = (LinearLayout) dialog.findViewById(R.id.number_layout);
            TextView title = (TextView) dialog.findViewById(R.id.subscription_title);
            final EditText phoneNumber = (EditText) dialog.findViewById(R.id.phone_number);
            Button cancelBtn = (Button) dialog.findViewById(R.id.rating_btn_cancel);
            Button submitBtn = (Button) dialog.findViewById(R.id.rating_btn_submit);
            ViewGroup.LayoutParams params = numberLayout.getLayoutParams();
            params.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            params.width = mywidth;
            numberLayout.setLayoutParams(params);
            //utility.setFonts(new View[]{title, cancelBtn, submitBtn});
            title.setText(utility.getLangauge().equals("bn") ? context.getString(R.string.number_msg_bn) : context.getString(R.string.number_msg_en));
            cancelBtn.setText(utility.getLangauge().equals("bn") ? context.getString(R.string.number_cancel_btn_bn) : context.getString(R.string.number_cancel_btn_en));
            submitBtn.setText(utility.getLangauge().equals("bn") ? context.getString(R.string.number_submit_btn_bn) : context.getString(R.string.number_submit_btn_en));
            cancelBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dialog.dismiss();
                }
            });
            submitBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dialog.dismiss();
                    String msisdn = "8801"+phoneNumber.getText().toString();
                    String message = utility.validateMsisdn(msisdn);
                    if(message.equals("OK")){
                        utility.writeMsisdn(msisdn);
                        checkMasterSubscription(trackId, position);
                    }
                    else{
                        utility.showToast(message);
                    }
                }
            });
            dialog.setCancelable(false);
            dialog.show();
        }
        catch (Exception ex){
            utility.showToast(ex.toString());
        }
    }

    private void checkMasterSubscription(final int trackId, final int position){
        utility.showProgress(false);
        try{
            RequestBody body = RequestBody.create(okhttp3.MediaType.parse("application/json; charset=utf-8"),(new JSONObject()).toString());
            Call<Subscription> call = apiInterface.getStatus(context.getString(R.string.authorization_key), utility.getMsisdn(), "", trackId, body);
            call.enqueue(new Callback<Subscription>() {
                @Override
                public void onResponse(Call<Subscription> call, Response<Subscription> response) {
                    utility.hideProgress();
                    if(response.isSuccessful()&&response.code()==200){
                        Subscription subscription = response.body();
                        utility.writeSubscriptionStatus(trackId, subscription);
                        if(utility.isSubscribed(trackId)) {
                            suggesationInterface.refreshPlayer(position);
                        }
                        else{
                            showPremiumDialog(trackId, position);
                        }
                    }
                    else{
                        utility.logger("Response is not successfull");
                    }
                }

                @Override
                public void onFailure(Call<Subscription> call, Throwable t) {
                    utility.hideProgress();
                    utility.logger(t.toString());
                }
            });
        }
        catch (Exception ex){
            utility.hideProgress();
            utility.logger(ex.toString());
        }
    }

    private void showPremiumDialog(final int trackId, final int position){
        Display display = ((Activity)context).getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        HashMap<String, Integer> screen = utility.getScreenRes();
        int width = screen.get(KeyWord.SCREEN_WIDTH);
        int height = screen.get(KeyWord.SCREEN_HEIGHT);
        int mywidth = (width / 10) * 7;
        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.setContentView(R.layout.dialog_layout);
        TextView tv = (TextView) dialog.findViewById(R.id.permission_message);
        Button yes = (Button) dialog.findViewById(R.id.dialog_yes);
        Button no = (Button) dialog.findViewById(R.id.dialog_no);
        tv.setText("এই কনটেন্টটি দেখতে আপনার জিপি ব্যালান্স থেকে "+utility.getPrice(trackId)+" টাকা চার্জ প্রযোজ্য। আপনি কি আগ্রহী?");
        LinearLayout ll = (LinearLayout) dialog.findViewById(R.id.dialog_layout_size);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) ll.getLayoutParams();
        params.height = LinearLayout.LayoutParams.WRAP_CONTENT;
        params.width = mywidth;
        ll.setLayoutParams(params);
        yes.setText(utility.getLangauge().equals("bn") ? context.getString(R.string.yes) : "Yes");
        no.setText(utility.getLangauge().equals("bn") ? context.getString(R.string.no) : "No");
        yes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                pushOtp(trackId,"0", position);
            }
        });
        no.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    private void pushOtp(final int trackId, String pin, final int position){
        utility.showProgress(false);
        try{
            RequestBody body = RequestBody.create(okhttp3.MediaType.parse("application/json; charset=utf-8"),(new JSONObject()).toString());
            Call<Subscription> call = apiInterface.pushOtp(context.getString(R.string.authorization_key), utility.getMsisdn(), "", trackId, body);
            call.enqueue(new Callback<Subscription>() {
                @Override
                public void onResponse(Call<Subscription> call, Response<Subscription> response) {
                    utility.hideProgress();
                    if(response.isSuccessful()&&response.code()==200){
                        Subscription subscription = response.body();
                        utility.writeSubscriptionStatus(trackId, subscription);
                        if (subscription.getComment().equals("generated")) {
                            validatePinDialog(trackId, position);
                        } else{
                            utility.showToast("PIN Process Failed");
                        }
                    }
                    else{
                        utility.logger("Response is not successfull");
                    }
                }

                @Override
                public void onFailure(Call<Subscription> call, Throwable t) {
                    utility.hideProgress();
                    utility.logger(t.toString());
                }
            });
        }
        catch (Exception ex){
            utility.hideProgress();
        }
    }

    private void validatePinDialog(final int trackId, final int position) {
        try {
            final Dialog dialog = new Dialog(context);
            HashMap<String, Integer> screenRes = utility.getScreenRes();
            int width = screenRes.get(KeyWord.SCREEN_WIDTH);
            int height = screenRes.get(KeyWord.SCREEN_HEIGHT);
            int mywidth = (width / 10) * 8;
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(R.layout.number_layout);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            LinearLayout numberLayout = (LinearLayout) dialog.findViewById(R.id.number_layout);
            TextView title = (TextView) dialog.findViewById(R.id.subscription_title);
            final EditText phoneNumber = (EditText) dialog.findViewById(R.id.phone_number);
            Button cancelBtn = (Button) dialog.findViewById(R.id.rating_btn_cancel);
            Button submitBtn = (Button) dialog.findViewById(R.id.rating_btn_submit);
            TextView phoneCode = (TextView) dialog.findViewById(R.id.phone_code);
            phoneCode.setVisibility(View.GONE);
            ViewGroup.LayoutParams params = numberLayout.getLayoutParams();
            params.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            params.width = mywidth;
            numberLayout.setLayoutParams(params);
            //utility.setFonts(new View[]{title, cancelBtn, submitBtn});
            title.setText(context.getString(R.string.number_pin_bn));
            phoneNumber.setHint("PIN e.g. XXXX");
            cancelBtn.setText(utility.getLangauge().equals("bn") ? context.getString(R.string.number_cancel_btn_bn) : context.getString(R.string.number_cancel_btn_en));
            submitBtn.setText(utility.getLangauge().equals("bn") ? context.getString(R.string.number_submit_btn_bn) : context.getString(R.string.number_submit_btn_en));
            cancelBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dialog.dismiss();
                }
            });
            submitBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String pin = phoneNumber.getText().toString();
                    if(pin.length()>0) {
                        dialog.dismiss();
                        chargeOtp(trackId, phoneNumber.getText().toString(), position);
                    }
                    else{
                        utility.showToast("Pin Required");
                    }
                }
            });
            dialog.setCancelable(false);
            dialog.show();
        }
        catch (Exception ex){
            utility.showToast(ex.toString());
        }
    }

    private void chargeOtp(final int trackId, String pin, final int position){
        utility.showProgress(false);
        try{
            RequestBody body = RequestBody.create(okhttp3.MediaType.parse("application/json; charset=utf-8"),(new JSONObject()).toString());
            Call<Subscription> call = apiInterface.chargeOtp(context.getString(R.string.authorization_key), utility.getMsisdn(), "", trackId, Integer.parseInt(pin), body);
            call.enqueue(new Callback<Subscription>() {
                @Override
                public void onResponse(Call<Subscription> call, Response<Subscription> response) {
                    utility.hideProgress();
                    if(response.isSuccessful()&&response.code()==200){
                        Subscription subscription = response.body();
                        utility.writeSubscriptionStatus(trackId, subscription);
                        if (subscription.getComment().equals("charged")) {
                            suggesationInterface.refreshPlayer(position);
                        } else{
                            utility.showToast("PIN Process Failed");
                        }
                    }
                    else{
                        utility.logger("Response is not successfull");
                    }
                }

                @Override
                public void onFailure(Call<Subscription> call, Throwable t) {
                    utility.hideProgress();
                    utility.logger(t.toString());
                }
            });
        }
        catch (Exception ex){
            utility.hideProgress();
        }
    }*/
}
