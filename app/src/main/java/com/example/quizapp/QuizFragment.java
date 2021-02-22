package com.example.quizapp;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import android.os.CountDownTimer;
import android.os.Handler;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class QuizFragment extends Fragment implements View.OnClickListener {

    // Declare
    private NavController navController;
    private final static String TAG = "QUIZ_FRAGMENT_LOG";
    private FirebaseFirestore firebaseFirestore;
    private FirebaseAuth firebaseAuth;
    private String quizId;
    private String quizName;

    // UI Elements
    private TextView quizTitle;
    private Button optionOneBtn, optionTwoBtn, optionThreeBtn, nextBtn;
    private ImageButton closeBtn;
    private TextView questionFeedback, questionText, questionTime, questionNumber;
    private ProgressBar questionProgress;

    // Firebase Data
    private List<QuestionsModel> allQuestionsList = new ArrayList<>();
    private long totalQuestionsToAnswer = 0;
    private List<QuestionsModel> questionsToAnswer = new ArrayList<>();
    private CountDownTimer countDownNumber;

    private boolean canAnswer = false;
    private static int currentQuestion;

    private int correctAnswers, wrongAnswers, notAnswered = 0 ;

    private String currentUserId;

    public QuizFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_quiz, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        currentQuestion = 0;

        navController = Navigation.findNavController(view);

        firebaseAuth = FirebaseAuth.getInstance();
        // Get User Id
        if (firebaseAuth.getCurrentUser() != null){
            currentUserId = firebaseAuth.getCurrentUser().getUid();
        }else {
            // Go back to HomePage

        }

        // Initializing
        firebaseFirestore = FirebaseFirestore.getInstance();
        quizTitle = view.findViewById(R.id.quiz_title);
        optionOneBtn = view.findViewById(R.id.quiz_option_one);
        optionTwoBtn = view.findViewById(R.id.quiz_option_two);
        optionThreeBtn = view.findViewById(R.id.quiz_option_three);
        nextBtn = view.findViewById(R.id.quiz_next_btn);
        questionFeedback = view.findViewById(R.id.quiz_question_feedback);
        questionText = view.findViewById(R.id.quiz_question);
        questionTime = view.findViewById(R.id.quiz_question_time);
        questionProgress = view.findViewById(R.id.quiz_question_progress);
        questionNumber = view.findViewById(R.id.quiz_question_number);

        // Get quizId & quizName & ... from DetailsFragment
        quizId = QuizFragmentArgs.fromBundle(getArguments()).getQuizId();
        totalQuestionsToAnswer = QuizFragmentArgs.fromBundle(getArguments()).getTotalQuestions();
        quizName = QuizFragmentArgs.fromBundle(getArguments()).getQuizName();

        // Get all questions from the quiz
        firebaseFirestore.collection("QuizList")
                .document(quizId).collection("Questions")
                .get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if (task.isSuccessful()){
                    // Add all questions to the list
                    allQuestionsList = task.getResult().toObjects(QuestionsModel.class);

                    // Pick Questions
                    pickQuestions();

                    loadUI();
                }else {
                    // Error getting Questions
                    quizTitle.setText("Error : " + task.getException().getMessage());
                }
            }
        });

        // Set Button Click Listener
        optionOneBtn.setOnClickListener(this);
        optionTwoBtn.setOnClickListener(this);
        optionThreeBtn.setOnClickListener(this);
        nextBtn.setOnClickListener(this);
    }

    private void loadUI() {
        // Quiz data loaded, Load the UI
        quizTitle.setText(quizName);
        questionText.setText("Load First Question");

        // Enable Options
        enableOptions();

        // Load First Question
        loadQuestion(1);
    }

    private void loadQuestion(int questionNum) {
        // Set Question Number
        questionNumber.setText(questionNum+"");

        // Load Question Text
        questionText.setText(questionsToAnswer.get(questionNum-1).getQuestion());

        // Load Options
        optionOneBtn.setText(questionsToAnswer.get(questionNum-1).getOption_a());
        optionTwoBtn.setText(questionsToAnswer.get(questionNum-1).getOption_b());
        optionThreeBtn.setText(questionsToAnswer.get(questionNum-1).getOption_c());

        // Question loaded, Set can answer
        canAnswer = true;
        currentQuestion = questionNum;

        // Start Question Timer
        startTimer(questionNum);
    }

    private void startTimer(int questionNumber) {
        // Set Timer Text
        Long timeToAnswer = questionsToAnswer.get(questionNumber-1).getTimer();
        questionTime.setText(timeToAnswer.toString());

        // Show Timer ProgressBar
        questionProgress.setVisibility(View.VISIBLE);

        // Start CountDown
        countDownNumber = new CountDownTimer(timeToAnswer*1000, 1){

            @Override
            public void onTick(long millisUntilFinished) {
                // Update Time
                questionTime.setText(millisUntilFinished/1000 + "");

                // Progress in Percent
                Long percent = millisUntilFinished/(timeToAnswer*10);
                questionProgress.setProgress(percent.intValue());
            }

            @Override
            public void onFinish() {
                // Time up, Can't answer question anymore
                canAnswer = false;

                questionFeedback.setText("Time Up! No answer was submitted.");
                questionFeedback.setTextColor(ResourcesCompat.getColor(getResources(), R.color.colorPrimary, null));
                notAnswered++;
                showNextBtn();
            }
        };

        countDownNumber.start();
    }

    private void enableOptions() {
        // Show all options buttons
        optionOneBtn.setVisibility(View.VISIBLE);
        optionTwoBtn.setVisibility(View.VISIBLE);
        optionThreeBtn.setVisibility(View.VISIBLE);

        // Enable Options Buttons
        optionOneBtn.setEnabled(true);
        optionTwoBtn.setEnabled(true);
        optionThreeBtn.setEnabled(true);

        // Hide Feedback and Next Button
        questionFeedback.setVisibility(View.INVISIBLE);
        nextBtn.setVisibility(View.INVISIBLE);
        nextBtn.setEnabled(false);
    }

    private void pickQuestions() {
        for (int i = 0; i<totalQuestionsToAnswer; i++){
            int randomNumber = getRandomInteger(allQuestionsList.size(), 0);
            questionsToAnswer.add(allQuestionsList.get(randomNumber));
            allQuestionsList.remove(randomNumber);
            Log.d(TAG, "Question "+ i + " : " + questionsToAnswer.get(i).getQuestion());
        }
    }

    public static int getRandomInteger(int maximum, int minimum){
        return ((int) (Math.random()*(maximum-minimum))) + minimum;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.quiz_option_one:
                verifyAnswer(optionOneBtn);
                break;
            case R.id.quiz_option_two:
                verifyAnswer(optionTwoBtn);
                break;
            case R.id.quiz_option_three:
                verifyAnswer(optionThreeBtn);
                break;
            case R.id.quiz_next_btn:
                if (currentQuestion == totalQuestionsToAnswer){
                    // Load Results
                    submitResults();
                }else {
                    currentQuestion++;
                    loadQuestion(currentQuestion);
                    resetOptions();
                }
        }
    }

    private void submitResults() {
        HashMap<String, Object> resultMap = new HashMap<>();
        resultMap.put("correct", correctAnswers);
        resultMap.put("wrong", wrongAnswers);
        resultMap.put("unanswered", notAnswered);

        firebaseFirestore.collection("QuizList")
                .document(quizId).collection("Results").document(currentUserId).set(resultMap)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()){
                    // Go to Results Page
                    QuizFragmentDirections.ActionQuizFragmentToResultFragment action = QuizFragmentDirections.actionQuizFragmentToResultFragment();
                    action.setQuizId(quizId);
                    navController.navigate(action);
                }else {
                    // Show Error
                    Toast.makeText(getContext(), "Error", Toast.LENGTH_LONG).show();
                    quizTitle.setText(task.getException().getMessage());
                }
            }
        });
    }

    private void resetOptions() {
        optionOneBtn.setBackground(ResourcesCompat.getDrawable(getContext().getResources(), R.drawable.outline_light_btn_bg, null));
        optionTwoBtn.setBackground(ResourcesCompat.getDrawable(getContext().getResources(), R.drawable.outline_light_btn_bg, null));
        optionThreeBtn.setBackground(ResourcesCompat.getDrawable(getContext().getResources(), R.drawable.outline_light_btn_bg, null));

        optionOneBtn.setTextColor(ResourcesCompat.getColor(getContext().getResources(), R.color.colorLightText, null));
        optionTwoBtn.setTextColor(ResourcesCompat.getColor(getContext().getResources(), R.color.colorLightText, null));
        optionThreeBtn.setTextColor(ResourcesCompat.getColor(getContext().getResources(), R.color.colorLightText, null));

        questionFeedback.setVisibility(View.INVISIBLE);
        nextBtn.setVisibility(View.INVISIBLE);
        nextBtn.setEnabled(false);
    }

    private void verifyAnswer(Button selectedAnswerBtn) {

        String selectedBtnText = (String) selectedAnswerBtn.getText();

        /*Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {*/

                // Check answer
                if (canAnswer){
                    // Set selectedAnswerBtn TextColor to Black
                    selectedAnswerBtn.setTextColor(ResourcesCompat.getColor(getContext().getResources(), R.color.colorDark, null));
                    String answer = questionsToAnswer.get(currentQuestion-1).getAnswer();
                    if (answer!=null && answer.equals(selectedBtnText)){
                        // Correct Answer
                        correctAnswers++;
                        selectedAnswerBtn.setBackground(ResourcesCompat.getDrawable(getContext().getResources(), R.drawable.correct_answer_btn_bg, null));

                        // Set feedback text
                        questionFeedback.setText("Correct Answer");
                        questionFeedback.setTextColor(ResourcesCompat.getColor(getContext().getResources(), R.color.colorPrimary, null));
                    }else if (!answer.equals(selectedBtnText)){
                        // Wrong Answer
                        wrongAnswers++;
                        selectedAnswerBtn.setBackground(ResourcesCompat.getDrawable(getContext().getResources(), R.drawable.wrong_answer_btn_bg, null));

                        // Set feedback text
                        questionFeedback.setText("Wrong Answer!!!\n\n");
                        questionFeedback.setTextColor(ResourcesCompat.getColor(getContext().getResources(), R.color.colorAccent, null));
                        String text = "<font color=#26D17E>Correct Answer is: </font> <font color=#929292>"+
                                answer+"</font>";
                        questionFeedback.append(Html.fromHtml(text));

                    }else {
                        Toast.makeText(getContext(), "There is a problem", Toast.LENGTH_SHORT).show();
                    }
                    // Set canAnswer false
                    canAnswer = false;

                    // Stop the Timer
                    countDownNumber.cancel();

                    // Show nextBtn
                    showNextBtn();
                }

            /*}
        }, 100);
*/

    }

    private void showNextBtn() {
        if (currentQuestion == totalQuestionsToAnswer){
            nextBtn.setText("Submit Results");
        }
        questionFeedback.setVisibility(View.VISIBLE);
        nextBtn.setVisibility(View.VISIBLE);
        nextBtn.setEnabled(true);
    }
}