package app.opensuite.one;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A real Android Views client for the OpenSuite One JSON API. No WebView is used. */
public final class MainActivity extends Activity {
    private static final int INK=Color.rgb(25,36,59);
    private static final int MUTED=Color.rgb(92,109,132);
    private static final int ACCENT=Color.rgb(55,92,229);
    private static final int SURFACE=Color.WHITE;
    private static final int BACKDROP=Color.rgb(245,247,252);
    private static final int LINE=Color.rgb(226,232,243);
    private static final String[] DESTINATIONS={"Home","Tasks","Notes","Calendar","Contacts","Files","Alerts","Search"};
    private final ApiClient api = new ApiClient();
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private LinearLayout root, content, navigation;
    private String page="Home", workspace="Workspace", displayName="Account";
    private SharedPreferences preferences;

    interface Job { JSONObject run() throws Exception; }
    interface Success { void show(JSONObject json) throws Exception; }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        preferences=getSharedPreferences("opensuite-local-settings",MODE_PRIVATE);
        showLogin();
    }
    @Override protected void onDestroy() { network.shutdownNow(); super.onDestroy(); }
    private int dp(float value) { return (int)(value*getResources().getDisplayMetrics().density+0.5f); }

    private GradientDrawable fill(int color, float radius) {
        GradientDrawable drawable=new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }
    private GradientDrawable outlined(int color) {
        GradientDrawable drawable=fill(color,15);
        drawable.setStroke(dp(1),LINE);
        return drawable;
    }
    private LinearLayout stack() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private TextView text(String label, int size, int color, boolean bold) {
        TextView v=new TextView(this);
        v.setText(label);v.setTextSize(size);v.setTextColor(color);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        v.setIncludeFontPadding(true);
        return v;
    }
    private LinearLayout.LayoutParams lp(int width, int height) {return new LinearLayout.LayoutParams(width<0?width:dp(width), height<0?height:dp(height));}
    private void padding(View v,int horizontal,int vertical) {v.setPadding(dp(horizontal),dp(vertical),dp(horizontal),dp(vertical));}
    private void gap(LinearLayout parent,int height) {parent.addView(new View(this),lp(1,height));}
    private void displayRoot() {
        root=stack();root.setBackgroundColor(BACKDROP);
        setContentView(root);
    }
    private TextView caption(String s) {TextView v=text(s,13,MUTED,false);v.setLineSpacing(dp(2),1f);return v;}
    private Button button(String text,boolean primary) {
        Button b=new Button(this);
        b.setText(text);b.setTextSize(14);
        b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));
        b.setTextColor(primary?Color.WHITE:INK);
        b.setBackground(primary?fill(ACCENT,14):outlined(SURFACE));
        b.setPadding(dp(14),0,dp(14),0);
        return b;
    }
    private EditText field(String value,String hint,int inputType) {
        EditText e=new EditText(this);
        e.setSingleLine(inputType != InputType.TYPE_CLASS_TEXT+InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setTextSize(15);e.setTextColor(INK);e.setHintTextColor(MUTED);
        e.setInputType(inputType);
        e.setText(value);e.setHint(hint);
        e.setBackground(outlined(SURFACE));
        e.setPadding(dp(14),dp(13),dp(14),dp(13));
        return e;
    }
    private void labeledInput(LinearLayout target,String name,EditText input){
        TextView label=text(name,13,INK,true);
        target.addView(label);gap(target,5);target.addView(input,lp(-1,-2));gap(target,14);
    }
    private void task(Job job,Success after) {
        network.execute(() -> {
            try {
                JSONObject response=job.run();
                runOnUiThread(() -> {
                    if(isFinishing()||isDestroyed())return;
                    try{after.show(response);}catch(Exception ex){error(ex.getMessage());}
                });
            } catch(Exception ex){
                runOnUiThread(() -> { if(!isFinishing()&&!isDestroyed())error(ex.getMessage()); });
            }
        });
    }
    private void error(String error) {
        String message=error==null?"Something went wrong":error;
        Toast.makeText(this,message,Toast.LENGTH_LONG).show();
        if(!api.hasSession() && !"Home".equals(page) && root!=null) showLogin();
    }

    private void showLogin() {
        page="Login";
        displayRoot();
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        root.addView(scroll,lp(-1,-1));
        LinearLayout screen=stack();padding(screen,24,30);scroll.addView(screen);
        LinearLayout identity=row();
        TextView logo=text("▦",28,Color.WHITE,true);logo.setGravity(Gravity.CENTER);logo.setBackground(fill(ACCENT,16));
        identity.addView(logo,lp(52,52));
        TextView brand=text("  OpenSuite One",24,INK,true);identity.addView(brand);
        screen.addView(identity);gap(screen,45);
        screen.addView(text("Your workspace. In one place.",29,INK,true));gap(screen,7);
        screen.addView(caption("Native Android client for your self-hosted OpenSuite One workspace."));gap(screen,30);
        EditText server=field(preferences.getString("server",BuildConfig.DEBUG?"http://10.0.2.2:4173":""),"https://your-server.example",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        EditText email=field("","you@company.com",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password=field("","Your password",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        labeledInput(screen,"Server URL",server);labeledInput(screen,"Email",email);labeledInput(screen,"Password",password);
        Button signIn=button("Sign in to workspace  →",true);screen.addView(signIn,lp(-1,52));gap(screen,16);
        TextView tip=caption("Your organization must be created on the server first. This app does not host the server, store your password, or send data to a third party.");
        screen.addView(tip);gap(screen,12);
        TextView debug=caption(BuildConfig.DEBUG?"Development APK: HTTP is available for emulator/LAN testing. Emulator server: http://10.0.2.2:4173. On a physical device use your computer's reachable LAN IP or a secure HTTPS domain.":"Release app requires an HTTPS server URL.");
        screen.addView(debug);
        signIn.setOnClickListener(v->{
            signIn.setEnabled(false);signIn.setText("Connecting…");
            try {
                api.configure(server.getText().toString());
                preferences.edit().putString("server",server.getText().toString().trim()).apply();
                task(() -> {
                    api.login(email.getText().toString(),password.getText().toString());
                    return api.request("GET","/api/me",null);
                },me -> {
                    workspace=me.getJSONObject("tenant").getString("name");
                    displayName=me.getJSONObject("user").getString("name");
                    page="Home";renderShell();loadCurrent();
                });
            }catch(Exception ex){error(ex.getMessage());}
            signIn.setEnabled(true);signIn.setText("Sign in to workspace  →");
        });
    }

    private void renderShell() {
        displayRoot();
        LinearLayout header=stack();header.setBackgroundColor(INK);padding(header,20,18);
        TextView small=text("OPENSUITE  /  " + workspace.toUpperCase(java.util.Locale.ROOT),11,Color.rgb(169,185,223),true);
        header.addView(small);gap(header,6);
        LinearLayout headerBottom=row();
        TextView heading=text(page,24,Color.WHITE,true);
        headerBottom.addView(heading,new LinearLayout.LayoutParams(0,dp(44),1));
        Button reload=button("↻",false);reload.setContentDescription("Refresh current view");reload.setOnClickListener(v->loadCurrent());
        headerBottom.addView(reload,lp(48,44));header.addView(headerBottom);
        root.addView(header);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        content=stack();padding(content,18,18);
        scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        HorizontalScrollView tabs=new HorizontalScrollView(this);tabs.setHorizontalScrollBarEnabled(false);tabs.setBackgroundColor(SURFACE);
        navigation=row();padding(navigation,8,9);
        tabs.addView(navigation);
        root.addView(tabs,lp(-1,-2));
        for(String destination:DESTINATIONS) {
            Button tab=button(destination,false);
            boolean active=destination.equals(page);
            tab.setTextColor(active?Color.WHITE:MUTED);
            tab.setBackground(fill(active?ACCENT:SURFACE,12));
            LinearLayout.LayoutParams item=lp(-2,46);item.setMargins(dp(3),0,dp(3),0);
            navigation.addView(tab,item);
            tab.setContentDescription("Open " + destination);
            tab.setOnClickListener(v->{page=destination;renderShell();loadCurrent();});
        }
    }

    private void sectionHeader(String title,String explanation) {
        content.addView(text(title,22,INK,true));gap(content,6);
        content.addView(caption(explanation));gap(content,18);
    }
    private void showLoading() {
        content.removeAllViews();gap(content,35);
        ProgressBar progress=new ProgressBar(this);content.addView(progress,lp(-1,40));gap(content,12);
        TextView status=caption("Loading " + page.toLowerCase(java.util.Locale.ROOT) + "…");status.setGravity(Gravity.CENTER);
        content.addView(status);
    }
    private void loadCurrent() {
        showLoading();
        String path;
        switch(page){
            case "Home": path="/api/summary";break;
            case "Tasks": path="/api/tasks";break;
            case "Notes": path="/api/notes";break;
            case "Calendar": path="/api/events";break;
            case "Contacts": path="/api/contacts";break;
            case "Files": path="/api/files";break;
            case "Alerts": path="/api/notifications";break;
            case "Search": showSearch();return;
            default: return;
        }
        String requestedPage=page;
        task(()->api.request("GET",path,null),data->{
            if (!page.equals(requestedPage)) return;
            content.removeAllViews();
            if(page.equals("Home"))showHome(data);
            else if(page.equals("Files"))showFiles(data);
            else if(page.equals("Alerts"))showAlerts(data);
            else showRecords(data);
        });
    }
    private void card(LinearLayout parent,String title,String body,Runnable click) {
        LinearLayout card=stack();padding(card,16,16);card.setBackground(outlined(SURFACE));
        TextView label=text(title,16,INK,true);card.addView(label);
        if(body!=null&&!body.isEmpty()){gap(card,7);card.addView(caption(body));}
        if(click!=null) {
            card.setClickable(true);card.setFocusable(true);
            card.setContentDescription(title+". Double tap for details and editing");
            card.setOnClickListener(v->click.run());
        }
        LinearLayout.LayoutParams params=lp(-1,-2);params.setMargins(0,0,0,dp(11));parent.addView(card,params);
    }
    private void showHome(JSONObject data) {
        sectionHeader("Welcome, " + displayName.split(" ")[0],"Actual numbers from " + workspace + ". No sample records.");
        JSONObject counts=data.optJSONObject("counts");
        if(counts!=null){
            card(content,"My tasks",counts.optInt("tasks")+" total · "+data.optInt("completedTasks")+" completed",()->go("Tasks"));
            card(content,"Notes",counts.optInt("notes")+" saved notes",()->go("Notes"));
            card(content,"Calendar",counts.optInt("events")+" events",()->go("Calendar"));
            card(content,"Contacts",counts.optInt("contacts")+" people",()->go("Contacts"));
        }
        gap(content,6);
        Button create=button("+ Add task",true);content.addView(create,lp(-1,48));create.setOnClickListener(v->openEditor("tasks",null));gap(content,14);
        card(content,"Activity","You have " + data.optInt("unread") + " unread notifications.",()->go("Alerts"));
        Button signOut=button("Sign out",false);content.addView(signOut,lp(-1,48));
        signOut.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Sign out?")
            .setMessage("Your session will be invalidated on the server.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Sign out",(d,w)->task(()->api.request("POST","/api/logout",new JSONObject()),ignored->{api.clear();showLogin();})).show());
    }
    private String kindForPage(){
        switch(page){case "Tasks":return "tasks";case "Notes":return "notes";case "Calendar":return "events";case "Contacts":return "contacts";default:return "";}
    }
    private void go(String selected){page=selected;renderShell();loadCurrent();}
    private void showRecords(JSONObject data) {
        String kind=kindForPage();
        String title=page.equals("Calendar")?"Events":page;
        sectionHeader(title,"Workspace records, synchronized with your server.");
        Button add=button("+ New " + (kind.equals("events")?"event":kind.substring(0,kind.length()-1)),true);
        content.addView(add,lp(-1,48));add.setOnClickListener(v->openEditor(kind,null));gap(content,16);
        JSONArray items=data.optJSONArray("items");
        if(items==null || items.length()==0){card(content,"Nothing here yet","Tap New to create your first record.",null);return;}
        for(int i=0;i<items.length();i++) {
            JSONObject item=items.optJSONObject(i);if(item==null)continue;
            String itemTitle=item.optString(kind.equals("contacts")?"name":"title","Untitled");
            String detail;
            switch(kind){
                case "tasks":detail=item.optString("status","todo") + " · " + item.optString("priority","normal") + " priority";break;
                case "notes":detail=item.optString("body","");if(detail.length()>100)detail=detail.substring(0,100)+"…";break;
                case "events":detail=item.optString("starts_at","") + " · " + item.optString("location","");break;
                case "contacts":detail=item.optString("email","") + " · " + item.optString("company","");break;
                default:detail="";
            }
            card(content,itemTitle,detail,()->openEditor(kind,item));
        }
    }
    private void showFiles(JSONObject result) {
        sectionHeader("Files","Optional Nextcloud integration — read-only in this milestone.");
        if(!result.optBoolean("configured",false)){
            card(content,"Not configured",result.optString("reason","Ask the server administrator to set up Nextcloud."),null);return;
        }
        JSONArray files=result.optJSONArray("files");
        if(files==null||files.length()==0){card(content,"Empty directory","No entries returned by the configured Nextcloud service.",null);return;}
        for(int i=0;i<files.length();i++)card(content,files.optJSONObject(i).optString("name","File"),"Read-only server listing",null);
    }
    private void showAlerts(JSONObject result) {
        sectionHeader("Notifications","Recent updates across your workspace.");
        JSONArray items=result.optJSONArray("items");
        if(items==null||items.length()==0){card(content,"All clear","You have no notifications.",null);return;}
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);if(item==null)continue;
            String label=item.optString("title","Notice");
            String detail=item.optString("body","") + (item.isNull("read_at")?" · Unread":" · Read");
            card(content,label,detail,()->task(()->api.request("POST","/api/notifications/read",new JSONObject().put("id",item.optString("id"))),v->loadCurrent()));
        }
    }
    private void showSearch() {
        content.removeAllViews();sectionHeader("Search","Search your permitted tasks, notes, events and contacts.");
        EditText query=field("","Title or name",InputType.TYPE_CLASS_TEXT);
        content.addView(query,lp(-1,-2));gap(content,12);
        Button search=button("Search workspace",true);content.addView(search,lp(-1,48));gap(content,16);
        LinearLayout resultsContainer=stack();content.addView(resultsContainer);
        search.setOnClickListener(v->{
            String term=query.getText().toString().trim();
            if(term.isEmpty()){error("Enter a search term");return;}
            task(()->api.request("GET","/api/search?q="+URLEncoder.encode(term,"UTF-8"),null),data->{
                JSONArray results=data.optJSONArray("results");
                resultsContainer.removeAllViews();
                if(results==null||results.length()==0){card(resultsContainer,"No matches","Try another title or name.",null);return;}
                for(int i=0;i<results.length();i++){
                    JSONObject result=results.optJSONObject(i);if(result==null)continue;
                    card(resultsContainer,result.optString("title"),result.optString("type"),()->{
                        String next=result.optString("type");
                        switch(next){case "tasks":go("Tasks");break;case "notes":go("Notes");break;case "events":go("Calendar");break;case "contacts":go("Contacts");break;}
                    });
                }
            });
        });
    }

    private void openEditor(String kind,JSONObject existing) {
        boolean editing=existing!=null;
        LinearLayout form=stack();padding(form,18,8);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        Map<String,EditText> inputs=new LinkedHashMap<>();
        String[][] schema;
        switch(kind){
            case "tasks":schema=new String[][]{{"title","Task title"},{"description","Description"},{"status","todo / in_progress / done"},{"priority","low / normal / high"},{"due_date","Optional ISO date, e.g. 2027-02-03"}};break;
            case "notes":schema=new String[][]{{"title","Title"},{"body","Text"}};break;
            case "events":schema=new String[][]{{"title","Event title"},{"location","Location"},{"starts_at","Start (ISO 8601)"},{"ends_at","End (ISO 8601)"}};break;
            case "contacts":schema=new String[][]{{"name","Full name"},{"email","Email"},{"company","Company"},{"job_title","Job title"}};break;
            default:return;
        }
        for(String[] spec:schema) {
            String initial=editing?existing.optString(spec[0],""):"";
            if(!editing && spec[0].equals("status"))initial="todo";
            if(!editing && spec[0].equals("priority"))initial="normal";
            int type=InputType.TYPE_CLASS_TEXT;
            if(spec[0].equals("description")||spec[0].equals("body"))type|=InputType.TYPE_TEXT_FLAG_MULTI_LINE;
            if(spec[0].equals("email"))type|=InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
            EditText input=field(initial,spec[1],type);
            if(spec[0].equals("description")||spec[0].equals("body")){input.setSingleLine(false);input.setMinLines(3);}
            labeledInput(form,spec[1],input);inputs.put(spec[0],input);
            if(spec[0].equals("starts_at")||spec[0].equals("ends_at")) {
                Button picker=button("Choose date & time",false);
                form.addView(picker,lp(-1,44));gap(form,10);
                picker.setOnClickListener(v->pickDateTime(input));
            }
        }
        AlertDialog editor=new AlertDialog.Builder(this)
            .setTitle(editing?"Edit " + kind.substring(0,kind.length()-1):"New " + kind.substring(0,kind.length()-1))
            .setView(scroll)
            .setNegativeButton("Cancel",(d,w)->{})
            .setPositiveButton("Save",null)
            .create();
        editor.setOnShowListener(ignored->{
            editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                try{
                    JSONObject body=new JSONObject();
                    for(Map.Entry<String,EditText> entry:inputs.entrySet()){
                        String value=entry.getValue().getText().toString().trim();
                        if(!value.isEmpty())body.put(entry.getKey(),value);
                        else if(editing && !"title".equals(entry.getKey()) && !"name".equals(entry.getKey()))body.put(entry.getKey(),value);
                    }
                    if(kind.equals("events")&&(!body.has("starts_at")||!body.has("ends_at")))throw new Exception("Choose start and end dates");
                    if(!body.has(kind.equals("contacts")?"name":"title"))throw new Exception("A title or name is required");
                    String resource="/api/"+kind;
                    String method=editing?"PATCH":"POST";
                    if(editing){resource+="/"+existing.getString("id");body.put("version",existing.getInt("version"));}
                    String route=resource;
                    editor.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    task(()->api.request(method,route,body),answer->{editor.dismiss();loadCurrent();});
                }catch(Exception e){error(e.getMessage());}
            });
        });
        editor.show();
        if(editing){
            Button remove=button("Delete record",false);
            form.addView(remove,lp(-1,44));
            remove.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("Delete this record?").setMessage("This cannot be undone.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Delete",(d,w)->task(()->api.request("DELETE","/api/"+kind+"/"+existing.optString("id"),null),answer->{editor.dismiss();loadCurrent();}))
                .show());
        }
    }
    private void pickDateTime(EditText input) {
        Calendar now=Calendar.getInstance();
        new DatePickerDialog(this,(datePicker,year,month,day)->{
            new TimePickerDialog(this,(timePicker,hour,minute)->{
                LocalDateTime local=LocalDateTime.of(year,month+1,day,hour,minute);
                Instant value=local.atZone(ZoneId.systemDefault()).toInstant();
                input.setText(value.toString());
            },now.get(Calendar.HOUR_OF_DAY),now.get(Calendar.MINUTE),true).show();
        },now.get(Calendar.YEAR),now.get(Calendar.MONTH),now.get(Calendar.DAY_OF_MONTH)).show();
    }
}
