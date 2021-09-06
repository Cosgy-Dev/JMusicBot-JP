package dev.cosgy.JMusicBot.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.settings.Settings;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * @author kosugikun
 */
public class MaintenanceInfo {
    private static JsonNode root;
    public String Title;
    public String Content;
    public String StartTime;
    public String EndTime;
    public String LastUpdate;

    public static boolean Verification() throws IOException {
        //アナウンスを行うか確認
        ObjectMapper mapper = new ObjectMapper();
        root = mapper.readTree(new URL("https://cosgy.dev/botinfo/info.json"));
        return root.get("setting").get(0).get("Announce").asBoolean();
    }

    public static MaintenanceInfo GetInfo() throws IOException {
        Logger log = LoggerFactory.getLogger("GetInfo");

        ObjectMapper mapper = new ObjectMapper();
        root = mapper.readTree(new URL("https://cosgy.dev/botinfo/info.json"));

        MaintenanceInfo Info = new MaintenanceInfo();

        //臨時メンテナンスか確認
        if (root.get("setting").get(0).get("emergency").asBoolean()) {
            Info.Title = root.get("emergencyInfo").get(0).get("Title").asText();
            Info.Content = root.get("emergencyInfo").get(0).get("Content").asText();
            Info.StartTime = root.get("emergencyInfo").get(0).get("StartTime").asText();
            Info.EndTime = root.get("emergencyInfo").get(0).get("StartTime").asText();
        } else {
            int InfoID = root.get("setting").get(0).get("InfoID").asInt();
            Info.Title = root.get("Normal").get(InfoID).get("Title").asText();
            Info.Content = root.get("Normal").get(InfoID).get("Content").asText();
            Info.StartTime = root.get("Normal").get(InfoID).get("StartTime").asText();
            Info.EndTime = root.get("Normal").get(InfoID).get("EndTime").asText();
        }
        Info.LastUpdate = root.get("setting").get(0).get("LastUpdate").asText();
        return Info;

    }

    public static void CommandInfo(CommandEvent event) throws IOException, ParseException {
        Logger log = LoggerFactory.getLogger("AutoInfo");
        ObjectMapper mapper = new ObjectMapper();
        root = mapper.readTree(new URL("https://cosgy.dev/botinfo/info.json"));
        String Start1 = root.get("setting").get(0).get("AutoAnnounceStart").asText();
        String End1 = root.get("setting").get(0).get("AutoAnnounceEnd").asText();
        boolean Announce = root.get("setting").get(0).get("AutoAnnounce").asBoolean();
        int AnnounceID = root.get("setting").get(0).get("AutoAnnounceID").asInt();

        Calendar Now = Calendar.getInstance();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

        Date Start = sdf.parse(Start1);
        Date End = sdf.parse(End1);
        Date NowTime = Now.getTime();

        boolean StartBoolean = Start.before(NowTime);
        boolean EndBoolean = End.after(NowTime);
        log.info("Confirm start time: " + StartBoolean);
        log.info("Confirm end time: " + EndBoolean);
        log.info("StartTime: " + sdf.format(Start));
        log.info("EndTime: " + sdf.format(End));
        log.info("NowTime: " + sdf.format(NowTime));

        Settings s = event.getClient().getSettingsFor(event.getGuild());
        log.info("Saved AutoAnnounceID: " + s.getAnnounce());
        log.info("Announce Server ID: " + AnnounceID);
        if (Announce && AnnounceID > s.getAnnounce() && StartBoolean && EndBoolean) {
            MaintenanceInfo InfoResult = MaintenanceInfo.GetInfo();

            MessageBuilder builder = new MessageBuilder().append("**").append(InfoResult.Title).append("**");
            EmbedBuilder ebuilder = new EmbedBuilder()
                    .setColor(Color.orange)
                    .setDescription(InfoResult.Content);
            if (!InfoResult.StartTime.equals("")) {
                ebuilder.addField("開始時刻:", InfoResult.StartTime, false);
            }
            if (!InfoResult.EndTime.equals("")) {
                ebuilder.addField("終了時刻:", InfoResult.EndTime, false);
            }
            ebuilder.addField("更新日時:", InfoResult.LastUpdate, false)
                    .addField("現在時刻", sdf.format(NowTime), false)
                    .setFooter("※メンテナンス期間は予定なく変更する場合があります。", null);
            event.getChannel().sendMessage(builder.append(ebuilder.build()).build()).complete();
            s.setAnnounce(AnnounceID);
        }
    }
}
