package sample.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import sample.Main;
import sample.model.Task;
import sample.service.CollectionTaskList;
import java.io.*;
import java.net.SocketException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

public class Controller {

    public Label username;

    private volatile CollectionTaskList taskListImpl = new CollectionTaskList();

    private Stage mainStage;

    @FXML
    private TableView tableTaskList;

    @FXML
    private TableColumn<Task, String> columnName;

    @FXML
    private TableColumn<Task, String> columnTime;

    @FXML
    private Parent fxmlEdit;
    private Parent notifFxmlEdit;
    private FXMLLoader fxmlLoader = new FXMLLoader();
    private FXMLLoader notifFxmlLoader = new FXMLLoader();
    private EditDialogController editDialogController;
    private NotificationController notificationController;
    private Stage editDialogStage;
    private Stage NotificationStage;

    private Timer timer = new Timer();

    private static Map<Integer, TimerTask> taskList = new HashMap<>();

    private Resender resend;

    private class Resender extends Thread {

        private boolean stoped;

        /**
         * Прекращает пересылку сообщений
         */
        public void setStop() {
            stoped = true;
        }

        /**
         * Считывает все сообщения от сервера и печатает их в консоль.
         * Останавливается вызовом метода setStop()
         *
         * @see java.lang.Thread#run()
         */
        @Override
        public void run() {
            try {
                while (!stoped) {
                    CollectionTaskList temp = new CollectionTaskList();
                    temp.getTaskList().clear();
                    temp.getTaskList().addAll((List<Task>) Main.in.readObject());
                    System.out.println(temp.getTaskList());
                    fillData(temp);
                }
            } catch (SocketException ignored) {
            } catch (Exception e) {
            System.err.println("Ошибка при получении сообщения.");
            e.printStackTrace();
            }
        }
    }

    public void setMainStage(Stage mainStage) {
        this.mainStage = mainStage;
    }

    @FXML
    private void initialize() {
        resend = new Resender();
        resend.start();
        columnName.setCellValueFactory(new PropertyValueFactory<Task, String>("name"));
        columnTime.setCellValueFactory(new PropertyValueFactory<Task, String>("time"));
        sendData("load", new Task(Main.USER, "23:59"));

        tableTaskList.setItems(taskListImpl.getTaskList());
        initLoader();
    }

    public static void sendData(String action, Task task) {
        HashMap<String, Task> map = new HashMap<>();
        map.put(action, task);

        try {
            Main.out.writeObject(map);
            Main.out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void fillData(CollectionTaskList taskListImpl_temp) {
        taskListImpl.getTaskList().clear();

        List<TimerTask> temp = new ArrayList<>(taskList.values());
        for (TimerTask tt : temp) {
            tt.cancel();
        }
        taskList.clear();
        Task task;
        if (taskListImpl_temp.getTaskList().isEmpty()) {
            System.out.println("пустой");
            return;
        }
        for (int i = 0; i < taskListImpl_temp.getTaskList().size(); i++) {
                task = taskListImpl_temp.getTaskList().get(i);

                if (isActual(task)) {           // создание тасков, если время правильное
                    setTask(task);
                    taskListImpl.add(task);
                } else {
                    sendData("delete", task);
                }
        }
        Task.setStaticId(Collections.max(taskList.keySet()));
    }

    private void initLoader() {
        try {
            fxmlLoader.setLocation(getClass().getResource("/edit.fxml"));
            notifFxmlLoader.setLocation(getClass().getResource("/notification.fxml"));
            fxmlEdit = fxmlLoader.load();
            notifFxmlEdit = notifFxmlLoader.load();
            editDialogController = fxmlLoader.getController();
            notificationController = notifFxmlLoader.getController();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isActual(Task task) {
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(task.getTime());
            if (!Objects.equals(task.getName(), "") && ZonedDateTime.now().isBefore(zdt)){
                return true;
            } else throw new Exception();
        } catch(Exception e) {
            //alert("Неверный ввод");
        }
        return false;
    }

    private void setTask(Task task) {
        if (!isActual(task)) {
            return;
        }

        ZonedDateTime zdt = ZonedDateTime.parse(task.getTime());
        final boolean[] completed = {false};
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                notificationController.setLabel(task);
                Platform.runLater(() -> {           // "костыль", чтобы из non-javafx потока изменить UI
                    showNotification();
                    if (notificationController.isCompleted()) {
                        completed[0] = true;
                        taskListImpl.delete(task);
                        taskList.remove(task.getId());
                    } else {
                        completed[0] = false;
                        Task task1 = new Task();
                        task1.setName(task.getName());
                        task1.setTime(ZonedDateTime.parse(task.getTime()).plusMinutes(1).toString());   // пересоздаём задачу если не была нажата кнопка "завершить"
                        taskListImpl.add(task1);                                                        // ставим на минуту позже
                        setTask(task1);                                                                 //
                        taskListImpl.delete(task);
                        ((TimerTask)taskList.get(task.getId())).cancel();
                        taskList.remove(task.getId());
                    }
                });
            }
        };
        if (!completed[0]) {
            taskList.put(task.getId(), timerTask);
            timer.schedule(timerTask, (zdt.toInstant().toEpochMilli() - ZonedDateTime.now(ZoneId.systemDefault()).toInstant().toEpochMilli()));
        }
    }

    public void actionButtonPressed(ActionEvent actionEvent) {

        Object source = actionEvent.getSource();

        if (!(source instanceof Button)) {
            return;
        }

        Button clickedButton = (Button) source;

        switch (clickedButton.getId()) {
            case "btnAdd":
                Task task = new Task();
                editDialogController.setTask(task);
                showDialog();
                if (editDialogController.isChanged()) {
                    task = editDialogController.getTask();
                    if (isActual(task)) {
                        sendData("add", task);
                    } else {
                        alert("Неверный ввод");
                    }
                }
                break;

            /*case "btnEdit":
                task = (Task)tableTaskList.getSelectionModel().getSelectedItem();

                if (task != null) {
                    editDialogController.setTask(task);
                    showDialog();
                    if (editDialogController.isChanged()) {
                        Task task1 = new Task();
                        task1.setName(editDialogController.getTask().getName());
                        task1.setTime(editDialogController.getTask().getTime());

                        taskListImpl.add(task1);

                        setTask(task1);
                        ((TimerTask) taskList.get(task.getId())).cancel();  // завершили таск
                        taskList.remove(task.getId());                      // удалили из списка таймертасков
                        taskListImpl.delete(task);                          // удалили "из гуи таблицы"
                    }
                } else {
                    alert("Выберите задачу");
                }
                break;*/

            case "btnDelete":
                task = (Task) tableTaskList.getSelectionModel().getSelectedItem();

                if (task != null) {
                    sendData("delete", task);
                    //taskList.remove(task.getId());
                    //taskListImpl.delete(task);
                } else {
                    alert("Выберите задачу");
                }

                break;
        }
    }

    public void saveTasks(ActionEvent actionEvent) {
        sendData("save", new Task());
    }

    private void alert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Ошибка");
        alert.setHeaderText(message);
        alert.showAndWait();
    }

    private void showNotification() {
        if (NotificationStage == null) {
            NotificationStage = new Stage();
            NotificationStage.setTitle("Уведомление");
            NotificationStage.setMinHeight(150);
            NotificationStage.setMinWidth(300);
            NotificationStage.setResizable(false);
            NotificationStage.setScene(new Scene(notifFxmlEdit));
            NotificationStage.initModality(Modality.WINDOW_MODAL);
            NotificationStage.initOwner(mainStage);
        }
        NotificationStage.showAndWait();
    }

    private void showDialog() {
        if (editDialogStage == null) {
            editDialogStage = new Stage();
            editDialogStage.setTitle("Редактирование задачи");
            editDialogStage.setMinHeight(150);
            editDialogStage.setMinWidth(300);
            editDialogStage.setResizable(false);
            editDialogStage.setScene(new Scene(fxmlEdit));
            editDialogStage.initModality(Modality.WINDOW_MODAL);
            editDialogStage.initOwner(mainStage);
        }
        editDialogStage.showAndWait();
    }

    public void closeResender() {
        resend.setStop();

    }
}
