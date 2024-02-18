package com.demoproject.SpringDemoBoot.service;

import com.demoproject.SpringDemoBoot.config.BotConfig;
import com.demoproject.SpringDemoBoot.model.ads.Ads;
import com.demoproject.SpringDemoBoot.model.ads.AdsRepository;
import com.demoproject.SpringDemoBoot.model.user.User;
import com.demoproject.SpringDemoBoot.model.user.UserRepository;
import com.vdurmont.emoji.EmojiParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.demoproject.SpringDemoBoot.model.TextConstants.*;

@Component
public class TelegramBot extends TelegramLongPollingBot {
    private final BotConfig config;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AdsRepository adsRepository;

    public TelegramBot(BotConfig config) {
        super(config.getToken());
        this.config = config;

        try {
            this.execute(new SetMyCommands(createCommandsList(), new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
        }
    }

    private List<BotCommand> createCommandsList() {
        List<BotCommand> commandList = new ArrayList<>();
        commandList.add(new BotCommand(START_COMMAND, "get a welcome message"));
        commandList.add(new BotCommand(HELP_COMMAND, "info how to use this bot"));
        return commandList;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            switch (messageText) {
                case START_COMMAND -> {
                    registerUser(update.getMessage());
                    startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                }
                case HELP_COMMAND -> sendMessage(chatId, HELP_TEXT);
                case REGISTER_COMMAND -> register(chatId);
                default -> sendMessage(chatId, "Sorry, command was not recognised");
            }
        } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            String text = "";
            if (callbackData.equals(YES_BUTTON)) {
                text = "You pressed YES button";
            } else if (callbackData.equals(NO_BUTTON)) {
                text = "You pressed NO button";
            }
            executeEditMessageText(messageId, chatId, text);
        }
    }

    private void executeEditMessageText(long messageId, long chatId, String text) {
        EditMessageText message = new EditMessageText();
        message.setMessageId((int) messageId);
        message.setChatId(chatId);
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {

        }
    }

    private void register(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Do you really want to register?");
        message.setReplyMarkup(getRegisterInlineKeyboardMarkup());
        executeMessage(message);
    }

    private InlineKeyboardMarkup getRegisterInlineKeyboardMarkup() {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton yesButton = new InlineKeyboardButton();
        yesButton.setText(YES_TEXT);
        yesButton.setCallbackData(YES_BUTTON);
        row.add(yesButton);

        InlineKeyboardButton noButton = new InlineKeyboardButton();
        noButton.setText(NO_TEXT);
        noButton.setCallbackData(NO_BUTTON);
        row.add(noButton);

        rows.add(row);
        keyboardMarkup.setKeyboard(rows);
        return keyboardMarkup;
    }

    private void registerUser(Message msg) {
        Long chatId = msg.getChatId();
        if (userRepository.findById(chatId).isEmpty()) {
            Chat chat = msg.getChat();

            User user = new User();
            user.setChatId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setLastName(chat.getLastName());
            user.setUserName(chat.getUserName());
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));

            userRepository.save(user);
        }
    }

    private void startCommandReceived(long chatId, String firstName) {
        String answer = EmojiParser.parseToUnicode("Hi, " + firstName + ", nice to meet you! " + BLUSH_EMOJI);

        sendMessage(chatId, answer, true);
    }

    private void sendMessage(long chatId, String textToSend) {
        sendMessage(chatId, textToSend, false);
    }

    private void sendMessage(long chatId, String textToSend, boolean generateKeyBoardMarkup) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(textToSend);

        if (generateKeyBoardMarkup) {
            message.setReplyMarkup(generateKeyBoardMarkup(List.of(List.of(REGISTER_COMMAND, HELP_COMMAND))));
        }

        executeMessage(message);
    }

    private ReplyKeyboardMarkup generateKeyBoardMarkup(List<List<String>> rows) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();

        List<KeyboardRow> keyboardRows = rows.stream().map(row -> {
            KeyboardRow keyboardRow = new KeyboardRow();
            row.forEach(keyboardRow::add);
            return keyboardRow;
        }).collect(Collectors.toList());

        keyboardMarkup.setKeyboard(keyboardRows);
        return keyboardMarkup;
    }

    private void executeMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {

        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Scheduled(cron = "${cron.scheduler}")
    private void sendAds() {
        Iterable<Ads> ads = adsRepository.findAll();
        Iterable<User> users = userRepository.findAll();

        ads.forEach(ad -> {
            users.forEach(user -> {
                sendMessage(user.getChatId(), ad.getAd());
            });
            adsRepository.delete(ad);
        });
    }
}
