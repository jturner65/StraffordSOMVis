package UI;

import java.util.ArrayList;

import SOM_Strafford_PKG.SOM_StraffordMain;
import Utils.*;

//displays sidebar menu of interaction and functionality

public class mySideBarMenu extends myDispWindow{
	//booleans in main program - need to have labels in idx order, even if not displayed
	public final String[] truePFlagNames = {//needs to be in order of flags
			"Debug Mode",
			"Final init Done",
			"Save Anim", 		
			"Shift-Key Pressed",
			"Alt-Key Pressed",
			"Cntl-Key Pressed",
			"Click interact", 	
			"Drawing Curve",
			"Changing View",	
			"Stop Simulation",
			"Single Step",
			"Displaying Side Menu",
			"Displaying UI Menu",
			"Reverse Drawn Trajectory"
			};
	
	public final String[] falsePFlagNames = {//needs to be in order of flags
			"Debug Mode",	
			"Final init Done",
			"Save Anim", 		
			"Shift-Key Pressed",
			"Alt-Key Pressed",
			"Cntl-Key Pressed",
			"Click interact", 	
			"Drawing Curve",
			"Changing View",	 	
			"Run Simulation",
			"Single Step",
			"Displaying Side Menu",
			"Displaying UI Menu",
			"Reverse Drawn Trajectory"
			};
	
	public int[][] pFlagColors;
	
	public final int clkFlgsStY = 10;
	
	public final String[] StateBoolNames = {"Shift","Alt","Cntl","Click", "Draw","View"};
	//multiplier for displacement to display text label for stateboolnames
	public final float[] StrWdMult = new float[]{-3.0f,-3.0f,-3.0f,-3.2f,-3.5f,-2.5f};
	public int[][] stBoolFlagColors;

	//	//GUI Objects	
	//idx's of objects in gui objs array	
	public static final int 
		gIDX_TimeStep 			= 0;//, 
	public final int numGUIObjs = 0;							//# of gui objects for ui
	
	//private child-class flags - window specific
	public static final int 
			mseClickedInBtnsIDX 		= 0;					//the mouse was clicked in the button region of the menu and a click event was processed
	//private flag based buttons - ui menu won't have these
	public static final int numPrivFlags = 1;
	
	//GUI Buttons
	public float minBtnClkY;			//where buttons should start on side menu

	public static final String[] guiBtnRowNames = new String[]{ 
			"Raw Data/Ftr Processing","Post Proc Load And Map Config/Exec","DEBUG","File"};

	public static final int 
			//btnShowWinIdx = 0, 				//which window to show
			btnAuxFunc1Idx = 0,			//aux functionality 1
			btnAuxFunc2Idx = 1,			//aux functionality 2
			btnDBGSelCmpIdx = 2,			//debug
			btnFileCmdIdx = 3;				//load/save files
	//names for each row of buttons - idx 1 is name of row
	public final String[][] guiBtnNames = new String[][]{
		//new String[]{pa.winTitles[1], pa.winTitles[2]},							//display specific windows - multi-select/ always on if sel
		new String[]{"Func 1","Func 2","Func 3"},						//per-window user functions - momentary
		new String[]{"Func 1","Func 2","Func 3","Func 4","Func 5"},						//per-window user functions - momentary
		new String[]{"Dbg 1","Dbg 2","Dbg 3","Dbg 4","Dbg 5"},						//DEBUG - momentary
		new String[]{"Load Txt File","Save Txt File"}							//load an existing score, save an existing score - momentary		
	};
	//default names, to return to if not specified by user
	public final String[][] defaultUIBtnNames = new String[][]{
		//new String[]{pa.winTitles[1], pa.winTitles[2]},							//display specific windows - multi-select/ always on if sel
		new String[]{"Func 1","Func 2","Func 3"},					//per-window user functions - momentary
		new String[]{"Func 1","Func 2","Func 3","Func 4","Func 5"},			//per-window user functions - momentary
		new String[]{"Dbg 1","Dbg 2","Dbg 3","Dbg 4","Dbg 5"},						//DEBUG - momentary
		new String[]{"Load Txt File","Save Txt File"}							//load an existing score, save an existing score - momentary		
	};
	//whether buttons are momentary or not (on only while being clicked)
	public boolean[][] guiBtnInst = new boolean[][]{
		//new boolean[]{false,false},         						//display specific windows - multi-select/ always on if sel
		new boolean[]{false,false,false,false,false},                   //functionality - momentary
		new boolean[]{false,false,false,false,false},                   //functionality - momentary
		new boolean[]{false,false,false,false,false},                   		//debug - momentary
		new boolean[]{true,true},			              			//load an existing score, save an existing score - momentary	
	};		
	//whether buttons are waiting for processing to complete (for non-momentary buttons)
	public boolean[][] guiBtnWaitForProc = new boolean[][]{
		//new boolean[]{false,false},         						//display specific windows - multi-select/ always on if sel
		new boolean[]{false,false,false,false,false},                   //functionality - momentary
		new boolean[]{false,false,false,false,false},                   //functionality - momentary
		new boolean[]{false,false,false,false,false},                   		//debug - momentary
		new boolean[]{false,false},			              			//load an existing score, save an existing score - momentary	
	};			
	
	//whether buttons are disabled(-1), enabled but not clicked/on (0), or enabled and on/clicked(1)
	public int[][] guiBtnSt = new int[][]{
		//new int[]{1,0},                    					//display specific windows - multi-select/ always on if sel
		new int[]{0,0,0,0,0},                   					//debug - momentary
		new int[]{0,0,0,0,0},                   					//debug - momentary
		new int[]{0,0,0,0,0},                   					//debug - momentary
		new int[]{0,0}			              					//load an existing score, save an existing score - momentary	
	};
	
	public int[] guiBtnStFillClr;
	public int[] guiBtnStTxtClr;
	//row and column of currently clicked-on button (for display highlight as pressing)
	public int[] curBtnClick = new int[]{-1,-1};

	public mySideBarMenu(my_procApplet _p, String _n, int _flagIdx, int[] fc, int[] sc, float[] rd, float[] rdClosed, String _winTxt, boolean _canDrawTraj) {
		super(_p, _n, _flagIdx, fc, sc,  rd, rdClosed, _winTxt, _canDrawTraj);
		guiBtnStFillClr = new int[]{		//button colors based on state
				SOM_StraffordMain.gui_White,								//disabled color for buttons
				SOM_StraffordMain.gui_LightGray,								//not clicked button color
				SOM_StraffordMain.gui_LightBlue,									//clicked button color
			};
		guiBtnStTxtClr = new int[]{			//text color for buttons
				SOM_StraffordMain.gui_LightGray,									//disabled color for buttons
				SOM_StraffordMain.gui_Black,									//not clicked button color
				SOM_StraffordMain.gui_Black,									//clicked button color
			};			
		super.initThisWin(_canDrawTraj, false, true);
	}
	
	//set up initial colors for papplet's flags for display
	public void initPFlagColors(){
		pFlagColors = new int[pa.numBaseFlags][3];
		for (int i = 0; i < pa.numBaseFlags; ++i) { pFlagColors[i] = new int[]{(int) pa.random(150),(int) pa.random(100),(int) pa.random(150)}; }		
		stBoolFlagColors = new int[pa.numStFlagsToShow][3];
		stBoolFlagColors[0] = new int[]{255,0,0};
		stBoolFlagColors[1] = new int[]{0,255,0};
		stBoolFlagColors[2] = new int[]{0,0,255};		
		for (int i = 3; i < pa.numStFlagsToShow; ++i) { stBoolFlagColors[i] = new int[]{100+((int) pa.random(150)),150+((int) pa.random(100)),150+((int) pa.random(150))};		}
	}
	@Override
	protected void snapMouseLocs(int oldMouseX, int oldMouseY, int[] newMouseLoc){}//not a snap-to window
		
	//call this from each new window to set function names, if specified, when window gets focus
	public void setAllBtnNames(int rowIdx, String[] btnNames) {
		String[] replAra = ((null==btnNames) || (btnNames.length != guiBtnNames[rowIdx].length)) ? defaultUIBtnNames[rowIdx] : btnNames;
		for(int i=0;i<guiBtnNames[rowIdx].length;++i) {guiBtnNames[rowIdx][i]=replAra[i];}
	}//setFunctionButtonNames
	
	
	@Override
	//initialize all private-flag based UI buttons here - called by base class
	public void initAllPrivBtns(){
		truePrivFlagNames = new String[]{								//needs to be in order of flags
		};
		falsePrivFlagNames = new String[]{			//needs to be in order of flags
		};
		privModFlgIdxs = new int[]{};
		numClickBools = privModFlgIdxs.length;	
	}//
	
	@Override
	protected void initMe() {//init/reinit this window
		setFlags(closeable, false);
//		setFlags(uiObjsAreVert, true);
		initPrivFlags(numPrivFlags);		
	}	
	//set flag values and execute special functionality for this sequencer
	@Override
	public void setPrivFlags(int idx, boolean val){
		int flIDX = idx/32, mask = 1<<(idx%32);
		privFlags[flIDX] = (val ?  privFlags[flIDX] | mask : privFlags[flIDX] & ~mask);
		switch (idx) {//special actions for each flag
			case mseClickedInBtnsIDX : {
				
			}
		}
	}

	//initialize structure to hold modifiable menu regions
	@Override
	protected void setupGUIObjsAras(){						//called from super.initThisWin
		guiMinMaxModVals = new double [][]{	{}};//min max mod values		
		guiStVals = new double[]{};
		guiObjNames = new String[]{};		
		
		//idx 0 is treat as int, idx 1 is obj has list vals, idx 2 is object gets sent to windows
		guiBoolVals = new boolean [][]{{}};
		
		minBtnClkY = (pa.numFlagsToShow+3) * yOff + clkFlgsStY;										//start of buttons from under boolean flags	
		initUIClickCoords(rectDim[0] + .1f * rectDim[2],minBtnClkY + (guiBtnRowNames.length * 2.0f) * yOff,rectDim[0] + .99f * rectDim[2],0);//last val over-written by actual value in buildGuiObjs
		guiObjs = new myGUIObj[numGUIObjs];			//list of modifiable gui objects
		if(0!=numGUIObjs){
			buildGUIObjs(guiObjNames,guiStVals,guiMinMaxModVals,guiBoolVals, new double[]{xOff,yOff});
		} else {
			uiClkCoords[3] = uiClkCoords[1];		//set y start values
		}
	}
	
	//check if buttons clicked
	private boolean checkButtons(int mseX, int mseY){
		double stY = minBtnClkY + rowStYOff, endY = stY+yOff, stX = 0, endX, widthX; //btnLblYOff			
		for(int row=0; row<guiBtnRowNames.length;++row){
			widthX = rectDim[2]/(1.0f * guiBtnNames[row].length);
			stX =0;	endX = widthX;
			for(int col =0; col<guiBtnNames[row].length;++col){	
				if((pa.ptInRange(mseX, mseY,stX, stY, endX, endY)) && (guiBtnSt[row][col] != -1)){
					handleButtonClick(row,col);
					return true;
				}					
				stX += widthX;	endX += widthX; 
			}
			stY = endY + yOff+ rowStYOff;endY = stY + yOff;				
		}
		return false;
	}//handleButtonClick	
	//public void clearAllBtnStates(){for(int row=0; row<guiBtnRowNames.length;++row){for(int col =0; col<guiBtnNames[row].length;++col){if((guiBtnInst[row][col]) && (guiBtnSt[row][col] ==1)){	guiBtnSt[row][col] = 0;}}}}
	
	//turn off buttons that may be on and should be turned off - called at release of mouse - check for mouse loc before calling (in button region)?
	public void clearAllBtnStates(){
		if(this.getPrivFlags(mseClickedInBtnsIDX)) {
			//guiBtnWaitForProc should only be set for non-momentary buttons when they are pushed and cleared when whatever they are do is complete
			for(int row=0; row<guiBtnRowNames.length;++row){for(int col =0; col<guiBtnNames[row].length;++col){				
				if((guiBtnSt[row][col]==1) && (guiBtnInst[row][col]  || !guiBtnWaitForProc[row][col])){	guiBtnSt[row][col] = 0;}//btn is on, and either is momentary or it is not waiting for processing
			}}
			this.setPrivFlags(mseClickedInBtnsIDX, false);
		}
	}//clearAllBtnStates
	
	//set non-momentary buttons to be waiting for processing complete comand
	public void setWaitForProc(int row, int col) {
		if(!guiBtnInst[row][col]) {	guiBtnWaitForProc[row][col] = true;}		
	}
	//handle click on button region of menubar
	public void handleButtonClick(int row, int col){
		int val = guiBtnSt[row][col];//initial state, before being changed
		guiBtnSt[row][col] = (guiBtnSt[row][col] + 1)%2;//change state
		//if not momentary buttons, set wait for proc to true
		setWaitForProc(row,col);
		switch(row){
			//case btnShowWinIdx 		: {pa.handleShowWin(col, val);break;}
			case btnAuxFunc1Idx 		: //{pa.handleMenuBtnSelCmp(btnAuxFunc1Idx,col, val);break;}
			case btnAuxFunc2Idx 		: //{pa.handleMenuBtnSelCmp(btnAuxFunc2Idx,col, val);break;}
			case btnDBGSelCmpIdx  		: {pa.handleMenuBtnSelCmp(row, col, val);break;}//{pa.handleMenuBtnSelCmp(btnDBGSelCmpIdx,col, val);break;}
			case btnFileCmdIdx 			: {pa.handleFileCmd(col, val);break;}
		}				
	}	

	//handle the display of UI objects backed by a list
	@Override
	protected String getUIListValStr(int UIidx, int validx){
		switch(UIidx){
		}
		return "";
	}//dispUIListObj
	//uses passed time
	@Override //only send new values if actually new values
	protected void setUIWinVals(int UIidx){
		switch(UIidx){
//		//set lcl/global vals
//		case gIDX_UIElem2List 		: {
////			int sel = (int)guiObjs[UIidx].getVal() % keySigs.length;
////			if (sel != myDispWindow.glblKeySig.keyIdx){for(int i=1; i<pa.dispWinFrames.length; ++i){pa.dispWinFrames[i].setGlobalKeySigVal(sel);} pa.setFlags(pa.forceInKey,false); }			
//			break;}
//		case gIDX_UIElem3 	: 
//		case gIDX_UIElem3List 	: {
////			int tsDenom = timeSigDenom[(int)guiObjs[gIDX_UIElem3List].getVal() %timeSigDenom.length],
////					tsNum = (int)guiObjs[gIDX_TimeSigNum].getVal();
////			durType dType = pa.getDurTypeForNote(tsDenom);			
////			if((dType != glblBeatNote) || (glblTimeSig.beatPerMeas != tsNum) || (glblTimeSig.beatNote != tsDenom)){			
////				for(int i=1; i<pa.dispWinFrames.length; ++i){pa.dispWinFrames[i].setGlobalTimeSigVal(tsNum,tsDenom, dType);} 
////			}
//			break;}
//		case gIDX_UIElem1			: {
//			float tmpTempo = (float)guiObjs[UIidx].getVal();
////			if(PApplet.abs(tmpTempo - glblTempo) > pa.feps){for(int i=1; i<pa.dispWinFrames.length; ++i){pa.dispWinFrames[i].setGlobalTempoVal(tmpTempo);}}
//			break;}
		}			
	}//setUIWinVals
	@Override
	protected boolean hndlMouseMoveIndiv(int mouseX, int mouseY, myPoint mseClckInWorld){		return false;	}
	@Override
	protected boolean hndlMouseClickIndiv(int mouseX, int mouseY, myPoint mseClckInWorld, int mseBtn) {	
		if((!pa.ptInRange(mouseX, mouseY, rectDim[0], rectDim[1], rectDim[0]+rectDim[2], rectDim[1]+rectDim[3]))){return false;}//not in this window's bounds, quit asap for speedz
		int i = (int)((mouseY-(yOff + yOff + clkFlgsStY))/(yOff));					//TODO Awful - needs to be recalced, dependent on menu being on left
		if((i>=0) && (i<pa.numFlagsToShow)){
			pa.setBaseFlag(pa.flagsToShow.get(i),!pa.getBaseFlag(pa.flagsToShow.get(i)));return true;	}
		else if(pa.ptInRange(mouseX, mouseY, 0, minBtnClkY, uiClkCoords[2], uiClkCoords[1])){
			boolean clkInBtnRegion = checkButtons(mouseX, mouseY);
			if(clkInBtnRegion) { this.setPrivFlags(mseClickedInBtnsIDX, true);}
			return clkInBtnRegion;
		}//in region where clickable buttons are - uiClkCoords[1] is bottom of buttons
		return false;
	}
	@Override
	protected boolean hndlMouseDragIndiv(int mouseX, int mouseY,int pmouseX, int pmouseY, myPoint mouseClickIn3D, myVector mseDragInWorld, int mseBtn) {//regular UI obj handling handled elsewhere - custom UI handling necessary to call main window		
		boolean res = pa.dispWinFrames[pa.curFocusWin].hndlMouseDragIndiv(mouseX, mouseY,pmouseX, pmouseY, mouseClickIn3D, mseDragInWorld, mseBtn);
		return res;	}
	@Override
	public void hndlMouseRelIndiv() {	clearAllBtnStates();}

	private void drawSideBarBooleans(){
		//draw main booleans and their state
		pa.translate(10,yOff*2);
		pa.setColorValFill(SOM_StraffordMain.gui_Black);
		pa.text("Boolean Flags",0,yOff*.20f);
		pa.translate(0,clkFlgsStY);
		for(int idx =0; idx<pa.numFlagsToShow; ++idx){
			int i = pa.flagsToShow.get(idx);
			if(pa.getBaseFlag(i) ){												dispMenuTxtLat(truePFlagNames[i],pFlagColors[i], true);			}
			else {	if(truePFlagNames[i].equals(falsePFlagNames[i])) {		dispMenuTxtLat(truePFlagNames[i],new int[]{180,180,180}, false);}	
					else {													dispMenuTxtLat(falsePFlagNames[i],new int[]{0,255-pFlagColors[i][1],255-pFlagColors[i][2]}, true);}		
			}
		}	
	}//drawSideBarBooleans
	private void drawSideBarStateBools(){ //numStFlagsToShow
		pa.translate(110,10);
		float xTrans = (int)((pa.menuWidth-100) / pa.numStFlagsToShow);
		for(int idx =0; idx<pa.numStFlagsToShow; ++idx){
			dispBoolStFlag(StateBoolNames[idx],stBoolFlagColors[idx], pa.getBaseFlag(pa.stateFlagsToShow.get(idx)),StrWdMult[idx]);			
			pa.translate(xTrans,0);
		}
	}
	
	//draw UI buttons that control functions, debug and global load/save stubs
	private void drawSideBarButtons(){
		pa.translate(xOff*.5f,(float)minBtnClkY);
		pa.setFill(new int[]{0,0,0}, 255);
		for(int row=0; row<guiBtnRowNames.length;++row){
			pa.text(guiBtnRowNames[row],0,-yOff*.15f);
			pa.translate(0,rowStYOff);
			float xWidthOffset = rectDim[2]/(1.0f * guiBtnNames[row].length), halfWay;
			pa.pushMatrix();pa.pushStyle();
			pa.strokeWeight(1.0f);
			pa.stroke(0,0,0,255);
			pa.noFill();
			pa.translate(-xOff*.5f, 0);
			for(int col =0; col<guiBtnNames[row].length;++col){
				halfWay = (xWidthOffset - pa.textWidth(guiBtnNames[row][col]))/2.0f;
				pa.setColorValFill(guiBtnStFillClr[guiBtnSt[row][col]+1]);
				pa.rect(0,0,xWidthOffset, yOff);	
				pa.setColorValFill(guiBtnStTxtClr[guiBtnSt[row][col]+1]);
				pa.text(guiBtnNames[row][col], halfWay, yOff*.75f);
				pa.translate(xWidthOffset, 0);
			}
			pa.popStyle();	pa.popMatrix();						
			pa.translate(0,btnLblYOff);
		}
	}//drawSideBarButtons	
	@Override
	protected void drawOnScreenStuffPriv(float modAmtMillis) {}
	@Override//for windows to draw on screen
	protected void drawRightSideInfoBarPriv(float modAmtMillis) {}
	@Override
	protected void drawMe(float animTimeMod) {
		pa.pushMatrix();pa.pushStyle();
			drawSideBarBooleans();				//toggleable booleans 
		pa.popStyle();	pa.popMatrix();	
		pa.pushMatrix();pa.pushStyle();
			drawSideBarStateBools();				//lights that reflect various states
		pa.popStyle();	pa.popMatrix();	
		pa.pushMatrix();pa.pushStyle();			
			drawSideBarButtons();						//draw buttons
		pa.popStyle();	pa.popMatrix();	
		pa.pushMatrix();pa.pushStyle();
			drawGUIObjs();					//draw what global user-modifiable fields are currently available 
		pa.popStyle();	pa.popMatrix();			
		pa.pushMatrix();pa.pushStyle();
			pa.drawWindowGuiObjs();			//draw objects for window with primary focus
		pa.popStyle();	pa.popMatrix();	
	}
	
	@Override
	public void drawCustMenuObjs(){}	
	@Override
	protected void launchMenuBtnHndlr() {
		switch(curCustBtnType) {
		case mySideBarMenu.btnAuxFunc1Idx : {break;}//row 1 of menu side bar buttons
		case mySideBarMenu.btnAuxFunc2Idx : {break;}//row 2 of menu side bar buttons
		case mySideBarMenu.btnDBGSelCmpIdx : {break;}//row 3 of menu side bar buttons (debug)			
		}		
	}
	//no custom camera handling for menu , float rx, float ry, float dz are all now member variables of every window
	@Override
	protected void setCameraIndiv(float[] camVals){}
	@Override
	public void hndlFileLoad(String[] vals, int[] stIdx) {
		hndlFileLoad_GUI(vals, stIdx);
		
	}
	@Override
	public ArrayList<String> hndlFileSave() {
		ArrayList<String> res = hndlFileSave_GUI();

		return res;
	}
	@Override
	protected String[] getSaveFileDirNamesPriv() {return new String[]{"menuDir","menuFile"};	}
	@Override
	public void drawClickableBooleans() {	}//this is only for non-sidebar menu windows, to display their own personal buttons
	@Override
	protected void setVisScreenDimsPriv() {}
	@Override
	protected myPoint getMsePtAs3DPt(int mouseX, int mouseY){return pa.P(mouseX,mouseY,0);}
	@Override
	protected void initTrajStructs() {}
	@Override
	protected void endShiftKeyI() {}
	@Override
	protected void endAltKeyI() {}
	@Override
	protected void endCntlKeyI() {}
	@Override
	protected void closeMe() {}
	@Override
	protected void showMe() {}
	@Override
	protected void resizeMe(float scale) {}	
	@Override
	protected boolean simMe(float modAmtSec) {return false;}
	@Override
	protected void stopMe() {}
	@Override
	protected void addSScrToWinIndiv(int newWinKey){}
	@Override
	protected void addTrajToScrIndiv(int subScrKey, String newTrajKey){}
	@Override
	protected void delSScrToWinIndiv(int idx) {}	
	@Override
	protected void delTrajToScrIndiv(int subScrKey, String newTrajKey) {}		
	//no trajectory here
	@Override
	protected void processTrajIndiv(myDrawnSmplTraj drawnTraj){}	
	@Override
	protected void initDrwnTrajIndiv(){}
	@Override
	public String toString(){
		String res = super.toString();
		return res;
	}

}//mySideBarMenu