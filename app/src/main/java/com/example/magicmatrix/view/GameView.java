package com.example.magicmatrix.view;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Point;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.Toast;
import com.example.magicmatrix.activity.Game;
import com.example.magicmatrix.bean.GameItem;
import com.example.magicmatrix.config.Config;

import java.util.ArrayList;
import java.util.List;


public class GameView extends GridLayout implements View.OnTouchListener {

    //上次操作后的分数,此变量必须为static变量，不然撤回操作无法将分数变回上一次得分数
    private static int mScoreHistory;
    //游戏的行数，对应挑战难度(4)
    private int mGameLines;
    //当前游戏界面对应的矩阵
    private GameItem[][] mGameMatrix;
    //上次移动后保留的矩阵，必须为static，不然mGameMatrixHistory一直是0
    private static int[][] mGameMatrixHistory;
    //用于 临时 保存运算之后的矩阵
    private List<Integer> mCalList;
    //保存空白位置的矩阵
    private List<Point> mBlanks;
    //历史分数，即开始游戏后的最高分数
    private int mHighScore;

    //滑动过程中的起始点和终点坐标
    private int mStartX;
    private int mStartY;
    private int mEndX;
    private int mEndY;

    private int mKeyItemNum = -1;//标识是否已经合并过
    //目标分数，达到之后，算挑战成功
    private int mTarget = 2048;

    public GameView(Context context) {
        super(context);
        //初始化操作必须放在构造函数里面，不然GridLayout无法显示
        initGameMatrix();
    }

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initGameMatrix();
    }

    public GameView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initGameMatrix();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        initGameMatrix();
    }

    /**
     * 开始游戏
     */
    public void startGame(){
        initGameMatrix();
    }

    /**
     * 初始化View
     */
    private void initGameMatrix(){
        //初始化矩阵
        removeAllViews();
        mScoreHistory = 0;
        Config.SCORE = 0;
        Config.mGameLines = Config.mSp.getInt(Config.KEY_GAME_LINES,4);
        mGameLines = Config.mGameLines;
        mGameMatrix = new GameItem[mGameLines][mGameLines];
        mGameMatrixHistory = new int[mGameLines][mGameLines];
        mCalList = new ArrayList<>();
        mBlanks = new ArrayList<>();
        mHighScore = Config.mSp.getInt(Config.KEY_HIGH_SCORE,0);
        setColumnCount(mGameLines);
        setRowCount(mGameLines);
        setOnTouchListener(this);
        //初始化View参数
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        assert wm != null;
        Display display = wm.getDefaultDisplay();
        display.getMetrics(metrics);
        Config.mItemSize = metrics.widthPixels/Config.mGameLines;
        initGameView(Config.mItemSize);
    }

    /**
     * 初始化游戏界面
     * @param cardSize  卡片大小
     */
    private void initGameView(int cardSize) {
        removeAllViews();
        GameItem card;
        for (int i = 0; i < mGameLines; i++) {
            for (int j = 0; j < mGameLines; j++) {
                card = new GameItem(getContext(),0);
                addView(card,cardSize,cardSize);
                //初始化GameMatrix全部为0，空格List(mBlanks)为所有
                mGameMatrix[i][j] = card;
                mBlanks.add(new Point(i,j));
            }
        }
        //添加随机数字,先添加两个方块
        addRandomNum();
        addRandomNum();
    }

    /**
     * 生成随机数
     */
    private void addRandomNum(){
        getBlanks();
        if (mBlanks.size()>0){
            int randomNum = (int) (Math.random()*mBlanks.size());
            Point randomPoint = mBlanks.get(randomNum);
            mGameMatrix[randomPoint.x][randomPoint.y].setNum(Math.random()>0.2d?2:4);//出现2的几率为80%
            animCreate(mGameMatrix[randomPoint.x][randomPoint.y]);
        }
    }

    /**
     * 生成一个用户想要的数
     */
    private void addSuperNum(int num){
        getBlanks();
        if (mBlanks.size()>0){
            int randomNum = (int) (Math.random()*mBlanks.size());
            Point randomPoint = mBlanks.get(randomNum);
            mGameMatrix[randomPoint.x][randomPoint.y].setNum(num);
            animCreate(mGameMatrix[randomPoint.x][randomPoint.y]);
        }
    }

    /**
     * 生成动画
     */
    private void animCreate(GameItem target){
        ScaleAnimation sa = new ScaleAnimation(0.1f,1,0.1f,1,
                Animation.RELATIVE_TO_SELF,0.5f,
                Animation.RELATIVE_TO_SELF,0.5f);
        sa.setDuration(100);
        target.setAnimation(null);
        target.getItemView().startAnimation(sa);
    }

    /**
     * 获得空格Item数组
     */
    private void getBlanks() {
        mBlanks.clear();
        for (int i = 0; i < mGameLines; i++) {
            for (int j = 0; j < mGameLines; j++) {
                if (mGameMatrix[i][j].getNum() == 0){
                    mBlanks.add(new Point(i,j));
                }
            }
        }
    }

    /**
     * 触摸事件
     *
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()){
            case MotionEvent.ACTION_DOWN:
                saveHistoryMatrix();
                mStartX = (int) event.getX();
                mStartY = (int) event.getY();
                break;
            case MotionEvent.ACTION_UP:
                mEndX = (int) event.getX();
                mEndY = (int) event.getY();
                judgeDirection(mEndX - mStartX,mEndY - mStartY);
                if (isMoved()){
                    addRandomNum();
                    //修改显示分数
                    Game.getGameActivity().setScore(Config.SCORE,0);
                }
                checkCompleted();
                break;
            default:
                break;
        }
        return true;
    }

    /**
     * 判断游戏结束
     */
    private void checkCompleted() {
        int result = checkNums();
        if (result == 0){
            Toast.makeText(getContext(),"挑战失败",Toast.LENGTH_LONG).show();
        }else if (result == 2){
            Toast.makeText(getContext(),"挑战成功",Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 判断是否结束
     * 0:结束
     * 1:正常
     * 2:失败
     * @return 结果
     */
    private int checkNums() {
        getBlanks();
        if(mBlanks.size() == 0){
            for (int i = 0; i < mGameLines; i++) {
                for (int j = 0; j < mGameLines; j++) {
                    if (j < mGameLines - 1){
                        if (mGameMatrix[i][j].getNum() == mGameMatrix[i][j+1].getNum()){
                            return 1;
                        }
                    }
                    if (i < mGameLines-1){
                        if (mGameMatrix[i][j].getNum() == mGameMatrix[i+1][j].getNum()){
                            return 1;
                        }
                    }
                }
            }
            return 0;
        }
        for (int i = 0; i < mGameLines; i++) {
            for (int j = 0; j < mGameLines; j++) {
                if (mGameMatrix[i][j].getNum() == mTarget){
                    return 2;
                }
            }
        }
        return 1;
    }

    /**
     * 偏移量判断移动方向
     */
    private void judgeDirection(int offsetX, int offsetY) {
        //得到当前屏幕密度
        int density = getDeviceDensity();
        //滑动有效距离的最小值
        int slideDis = 5*density;
        //滑动有效距离超过之后，启用“后门”
        int maxDis = 200*density;
        boolean flagNormal = (Math.abs(offsetX)>slideDis||
                Math.abs(offsetY)>slideDis)&&
                (Math.abs(offsetX)<maxDis)&&
                (Math.abs(offsetY)<maxDis);
        boolean flagSuper = Math.abs(offsetX)>maxDis||Math.abs(offsetY)>maxDis;
        if (flagNormal&&!flagSuper){
            if (Math.abs(offsetX)>Math.abs(offsetY)){
                if (offsetX>slideDis){
                    swipeRight();
                }else{
                    swipeLeft();
                }
            }else{
                if (offsetY>slideDis){
                    swipeDown();
                }else{
                    swipeUp();
                }
            }
        }else if (flagSuper){//使用超级用户权限来添加自定义数字
//            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
//            final EditText et = new EditText(getContext());
//            builder.setTitle("Back Door")
//                    .setView(et)
//                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
//                        @Override
//                        public void onClick(DialogInterface dialog, int which) {
//                            if (!TextUtils.isEmpty(et.getText())){
//                                //在界面中生成一个用户想要的数
//                                addSuperNum(Integer.parseInt(et.getText().toString()));
//                                checkCompleted();
//                            }
//                        }
//                    })
//                    .setNegativeButton("ByeBye", new DialogInterface.OnClickListener() {
//                        @Override
//                        public void onClick(DialogInterface dialog, int which) {
//                            dialog.dismiss();
//                        }
//                    })
//                    .create()
//                    .show();
        }
    }

    /**
     * 得到屏幕密度信息
     * @return 屏幕密度信息
     */
    private int getDeviceDensity() {
        return (int) getContext().getResources().getDisplayMetrics().density;
    }

    /**
     * 滑动事件：左
     */
    private void swipeLeft(){
        for (int i = 0; i < mGameLines; i++) {
            for (int j = 0; j < mGameLines; j++) {
                int currentNum = mGameMatrix[i][j].getNum();
                if (currentNum != 0){
                    if (mKeyItemNum == -1){//判断是否合并过
                        mKeyItemNum = currentNum;
                    }else{
                        if (mKeyItemNum == currentNum){
                            mCalList.add(mKeyItemNum*2);
                            Config.SCORE += mKeyItemNum*2;
                            mKeyItemNum = -1;
                        }else{
                            mCalList.add(mKeyItemNum);
                            mKeyItemNum = currentNum;
                        }
                    }
                }
            }
            if (mKeyItemNum != -1){
                mCalList.add(mKeyItemNum);
            }
            //改变Item的值
            for (int j = 0; j < mCalList.size(); j++) {
                mGameMatrix[i][j].setNum(mCalList.get(j));
            }
            for (int j = mCalList.size(); j < mGameLines; j++) {
                mGameMatrix[i][j].setNum(0);
            }
            //重置行参数
            mKeyItemNum = -1;
            mCalList.clear();
        }
    }

    /**
     * 滑动事件：上
     */
    private void swipeUp(){
        for (int i = 0; i < mGameLines; i++) {
            for (int j = 0; j < mGameLines; j++) {
                int currentNum = mGameMatrix[j][i].getNum();
                if (currentNum != 0){
                    if (mKeyItemNum == -1){//判断是否合并过
                        mKeyItemNum = currentNum;
                    }else{
                        if (mKeyItemNum == currentNum){
                            mCalList.add(mKeyItemNum*2);
                            Config.SCORE += mKeyItemNum*2;
                            mKeyItemNum = -1;
                        }else{
                            mCalList.add(mKeyItemNum);
                            mKeyItemNum = currentNum;
                        }
                    }
                }
            }
            if (mKeyItemNum != -1){
                mCalList.add(mKeyItemNum);
            }
            //改变Item的值
            for (int j = 0; j < mCalList.size(); j++) {
                mGameMatrix[j][i].setNum(mCalList.get(j));
            }
            for (int j = mCalList.size(); j < mGameLines; j++) {
                mGameMatrix[j][i].setNum(0);
            }
            //重置行参数
            mKeyItemNum = -1;
            mCalList.clear();
        }
    }

    /**
     * 滑动事件：右
     */
    private void swipeRight(){
        for (int i = 0; i < mGameLines; i++) {
            for (int j = mGameLines-1; j >= 0; j--) {
                int currentNum = mGameMatrix[i][j].getNum();
                if (currentNum != 0){
                    if (mKeyItemNum == -1){//判断是否合并过
                        mKeyItemNum = currentNum;
                    }else{
                        if (mKeyItemNum == currentNum){
                            mCalList.add(mKeyItemNum*2);
                            Config.SCORE += mKeyItemNum*2;
                            mKeyItemNum = -1;
                        }else{
                            mCalList.add(mKeyItemNum);
                            mKeyItemNum = currentNum;
                        }
                    }
                }
            }
            if (mKeyItemNum != -1){
                mCalList.add(mKeyItemNum);
            }
            //改变Item的值
            for (int j = mGameLines-1,t=0; t < mCalList.size(); j--,t++) {
                mGameMatrix[i][j].setNum(mCalList.get(t));
            }
            for (int j = mGameLines-mCalList.size()-1; j >= 0; j--) {
                mGameMatrix[i][j].setNum(0);
            }
            //重置行参数
            mKeyItemNum = -1;
            mCalList.clear();
        }
    }

    /**
     * 滑动事件：下
     */
    private void swipeDown(){
        for (int i = 0; i < mGameLines; i++) {
            for (int j = mGameLines-1; j >= 0; j--) {
                int currentNum = mGameMatrix[j][i].getNum();
                if (currentNum != 0){
                    if (mKeyItemNum == -1){//判断是否合并过
                        mKeyItemNum = currentNum;
                    }else{
                        if (mKeyItemNum == currentNum){
                            mCalList.add(mKeyItemNum*2);
                            Config.SCORE += mKeyItemNum*2;
                            mKeyItemNum = -1;
                        }else{
                            mCalList.add(mKeyItemNum);
                            mKeyItemNum = currentNum;
                        }
                    }
                }
            }
            if (mKeyItemNum != -1){
                mCalList.add(mKeyItemNum);
            }
            //改变Item的值
            for (int j = mGameLines-1,t = 0; t < mCalList.size(); j--,t++) {
                mGameMatrix[j][i].setNum(mCalList.get(t));
            }
            for (int j = mGameLines-mCalList.size()-1; j >= 0; j--) {
                mGameMatrix[j][i].setNum(0);
            }
            //重置行参数
            mKeyItemNum = -1;
            mCalList.clear();
        }
    }

    /**
     * 保存历史矩阵
     */
    private void saveHistoryMatrix() {
        mScoreHistory = Config.SCORE;
        for (int i = 0; i < mGameLines; i++) {
            for (int j = 0; j < mGameLines; j++) {
                mGameMatrixHistory[i][j] = mGameMatrix[i][j].getNum();
            }
        }
    }

    /**
     * 判断是否移动过(是否需要新增加Item)
     */
    private boolean isMoved(){
        for (int i = 0; i < mGameLines; i++) {
            for (int j = 0; j < mGameLines; j++) {
                if (mGameMatrixHistory[i][j] != mGameMatrix[i][j].getNum()){
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 撤销上次移动
     */
    public void revertGame(){
        //第一次不能撤销
        int sum = 0;
        for (int[] element:mGameMatrixHistory){
            for (int i:element){
                sum += i;
            }
        }
        if (sum != 0){
            //修改显示分数
            Game.getGameActivity().setScore(mScoreHistory,0);
            Config.SCORE = mScoreHistory;
            for (int i = 0; i < mGameLines; i++) {
                for (int j = 0; j < mGameLines; j++) {
                    mGameMatrix[i][j].setNum(mGameMatrixHistory[i][j]);
                }
            }
        }
    }
}
