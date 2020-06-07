import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.*;
import javax.swing.*;

/**
 * The parser and interpreter. The top level parse function, a main method for
 * testing, and several utility methods are provided. You need to implement
 * parseProgram and all the rest of the parser.
 */
public class Parser {

	/**
	 * Top level parse method, called by the World
	 */
	static RobotProgramNode parseFile(File code) {
		Scanner scan = null;
		try {
			scan = new Scanner(code);

			// the only time tokens can be next to each other is
			// when one of them is one of (){},;
			scan.useDelimiter("\\s+|(?=[{}(),;])|(?<=[{}(),;])");

			RobotProgramNode n = parseProgram(scan); // You need to implement this!!!

			scan.close();
			return n;
		} catch (FileNotFoundException e) {
			System.out.println("Robot program source file not found");
		} catch (ParserFailureException e) {
			System.out.println("Parser error:");
			System.out.println(e.getMessage());
			scan.close();
		}
		return null;
	}

	/** For testing the parser without requiring the world */

	public static void main(String[] args) {
		if (args.length > 0) {
			for (String arg : args) {
				File f = new File(arg);
				if (f.exists()) {
					System.out.println("Parsing '" + f + "'");
					RobotProgramNode prog = parseFile(f);
					System.out.println("Parsing completed ");
					if (prog != null) {
						System.out.println("================\nProgram:");
						System.out.println(prog);
					}
					System.out.println("=================");
				} else {
					System.out.println("Can't find file '" + f + "'");
				}
			}
		} else {
			while (true) {
				JFileChooser chooser = new JFileChooser(".");// System.getProperty("user.dir"));
				int res = chooser.showOpenDialog(null);
				if (res != JFileChooser.APPROVE_OPTION) {
					break;
				}
				RobotProgramNode prog = parseFile(chooser.getSelectedFile());
				System.out.println("Parsing completed");
				if (prog != null) {
					System.out.println("Program: \n" + prog);
				}
				System.out.println("=================");
			}
		}
		System.out.println("Done");
	}

	// Useful Patterns

	static Pattern NUMPAT = Pattern.compile("-?\\d+"); // ("-?(0|[1-9][0-9]*)");
	static Pattern OPENPAREN = Pattern.compile("\\(");
	static Pattern CLOSEPAREN = Pattern.compile("\\)");
	static Pattern OPENBRACE = Pattern.compile("\\{");
	static Pattern CLOSEBRACE = Pattern.compile("\\}");
	static Pattern LOOPPAT = Pattern.compile("loop");
	static Pattern IFPAT = Pattern.compile("if");
	static Pattern ELIFPAT = Pattern.compile("elif");
	static Pattern WHILEPAT = Pattern.compile("while");
	static Pattern ACTPAT = Pattern.compile("move|turnL|turnR|takeFuel|wait|turnAround|shieldOn|shieldOff");
	static Pattern SENPAT = Pattern.compile("fuelLeft|oppLR|oppFB|numBarrels|barrelLR|barrelFB|wallDist");
	static Pattern OPPAT = Pattern.compile("sub|mul|div|add");
	static Pattern RELOPPAT = Pattern.compile("gt|lt|eq");
	static Pattern COMMAPAT = Pattern.compile(",");
	static Pattern SEMIPAT = Pattern.compile(";");
	static Pattern ELSEPAT = Pattern.compile("else");
	static Pattern VARPAT = Pattern.compile("\\$[A-Za-z][A-Za-z0-9]*");

	public static HashMap<String, Integer> variableMap= new HashMap<>();
	public static HashMap<String, variableNode> varNameMap = new HashMap<>();

	/**
	 * PROG ::= STMT+
	 */
	static RobotProgramNode parseProgram(Scanner s) {
		programNode node = new programNode();
		while(s.hasNext()){
			node.addNode(parseStatement(s));
		}
		return node;
	}

	// utility methods for the parser

	static RobotProgramNode parseStatement(Scanner s){
		statementNode node = new statementNode();

		if(s.hasNext(ACTPAT)) {
			node.addNode(parseAct(s));
		}
		else if(s.hasNext(LOOPPAT)) node.addNode(parseLoop(s));
		else if(s.hasNext(IFPAT)) node.addNode(parseIf(s));
		else if(s.hasNext(WHILEPAT)) node.addNode(parseWhile(s));
		else if(s.hasNext(VARPAT)) node.addNode(parseAssig(s));
		else fail("Not a valid statement", s);
		return node;
	}

	static RobotProgramNode parseWhile(Scanner s) {
		whileNode node = new whileNode();
		require(WHILEPAT, "Missing while keyword",s);
		require(OPENPAREN, "Missing ( in while statement",s);
		node.addCond(parseCond(s));
		require(CLOSEPAREN, "Missing ) in while statement",s);
		node.addNode(parseBlock(s));
		return node;
	}

	static RobotProgramNode parseIf(Scanner s) {
		ifNode node = new ifNode();
		require(IFPAT, "Missing if keyword",s);
		require(OPENPAREN, "Missing ( in if statement",s);
		node.addCond(parseCond(s));
		require(CLOSEPAREN, "Missing ) in if statement",s);
		node.addNode(parseBlock(s));
		if(s.hasNext(ELIFPAT)){
			do{
				node.addElif(parseElif(s));
			}while(s.hasNext(ELIFPAT));
		}
		if(s.hasNext(ELSEPAT)) {
			require(ELSEPAT, "Missing else keyword",s);
			node.setElseNode(parseBlock(s));
		}
		return node;
	}

	static ifNode parseElif(Scanner s){
		ifNode node = new ifNode();
		require(ELIFPAT, "Missing elif keyword", s);
		require(OPENPAREN, "Missing ( in if statement",s);
		node.addCond(parseCond(s));
		require(CLOSEPAREN, "Missing ) in if statement",s);
		node.addNode(parseBlock(s));
		return node;
	}

	static RobotEvaluateNode parseCond(Scanner s) {
		conditionNode node = new conditionNode();
		if(s.hasNext(RELOPPAT)){
			node.setRelop(parseRelop(s));
			require(OPENPAREN, "Missing ( in relop condition", s);
			node.setSen(parseExpr(s));
			require(COMMAPAT, "Missing a comma in relop condition", s);
			node.setNum(parseExpr(s));
		}else {
			String condString = s.next();
			switch (condString) {
				case "and":
					node.setCond(CONDS.and);
					require(OPENPAREN, "Missing ( in and condition", s);
					node.addCond(parseCond(s));
					require(COMMAPAT, "Missing a comma in and condition", s);
					node.addCond(parseCond(s));
					break;
				case "or":
					node.setCond(CONDS.or);
					require(OPENPAREN, "Missing ( in or condition", s);
					node.addCond(parseCond(s));
					require(COMMAPAT, "Missing a comma in or condition", s);
					node.addCond(parseCond(s));
					break;
				case "not":
					node.setCond(CONDS.not);
					require(OPENPAREN, "Missing ( in not condition", s);
					node.addCond(parseCond(s));
					break;
				default:
					fail("Not a valid conditional", s);
					break;
			}
		}
		require(CLOSEPAREN, "Missing ) in condition", s);
		return node;
	}

	static COMP parseRelop(Scanner s){
		COMP relop;
		relop = COMP.valueOf(require(RELOPPAT,"not a valid relop", s));
		return relop;
	}

	static RobotSensorNode parseNum(Scanner s){
		numberNode node = new numberNode();
		node.setNumber(requireInt(NUMPAT, "not a valid integer",s));
		return node;
	}

	static RobotSensorNode parseSen(Scanner s){
		sensorNode node = new sensorNode();
		String senString = require(SENPAT, "Not a valid sensor",s);
		try{
			node.setSen(SENSOR.valueOf(senString));
		}catch (IllegalArgumentException e){ fail("Not a valid sensor", s); }

		if((senString.equals("barrelLR") || senString.equals("barrelFB")) && s.hasNext(OPENPAREN)){
			require(OPENPAREN, "Missing ( in sensor", s);
			node.setIter(parseExpr(s));
			require(CLOSEPAREN, "Missing ) in sensor",s);
		}
		return node;
	}

	static RobotProgramNode parseAct(Scanner s){
		actNode node = new actNode();
		node.setIterations(null);
		String str = require(ACTPAT,"Not a valid action",s);
		try{
			node.setAction(ACTION.valueOf(str));
		}catch(IllegalArgumentException e){
			fail("Not a valid action", s);
		}
		if((str.equals("move") || str.equals("wait")) && s.hasNext(OPENPAREN)){
			require(OPENPAREN, "Missing ( in else",s);
			node.setIterations(parseExpr(s));
			require(CLOSEPAREN, "Missing ) in else",s);
		}
		require(SEMIPAT, "Missing semicolon",s);
		return node;
	}

	static RobotProgramNode parseAssig(Scanner s){
		assignmentNode node = new assignmentNode();
		node.setVariable(parseVariable(s));
		require("=", "Missing = in assignment", s);
		node.setExpression(parseExpr(s));
		require(SEMIPAT, "Missing ; in assignment", s);
		return node;
	}

	static variableNode parseVariable(Scanner s){
		variableNode node = new variableNode();
		String varString = require(VARPAT, "Not a valid variable name",s);

		node.setVariableName(varString);
		return node;
	}

	static RobotSensorNode parseExpr(Scanner s) {
		expressionNode node = new expressionNode();
		if(s.hasNext(SENPAT)) node.addNode(parseSen(s));
		else if(s.hasNext(NUMPAT)) node.addNode(parseNum(s));
		else if(s.hasNext(VARPAT)) node.addNode(parseVariable(s));
		else if(s.hasNext(OPPAT)){
			node.setOp(parseOp(s));
			require(OPENPAREN, "Missing ( in expression",s);
			node.addNode(parseExpr(s));
			require(COMMAPAT, "Missing comma in expression",s);
			node.addNode(parseExpr(s));
			require(CLOSEPAREN, "Missing ) in expression",s);
		}
		else fail("not a valid expression", s);
		return node;
	}

	static OP parseOp(Scanner s){
		OP operator = null;
		String str = require(OPPAT,"Not a valid action",s);
		try{
			operator = (OP.valueOf(str));
		}catch(IllegalArgumentException e){
			fail("Not a valid operator", s);
		}
		return operator;
	}

	static RobotProgramNode parseLoop(Scanner s){
		loopNode node = new loopNode();
		require(LOOPPAT, "not a valid loop",s);
		node.addNode(parseBlock(s));
		return node;
	}

	static RobotProgramNode parseBlock(Scanner s){
		blockNode node = new blockNode();
		require(OPENBRACE, "Missing \\{ for block",s);
 		do {
 			node.addNode(parseStatement(s));
		}while(!s.hasNext(CLOSEBRACE));
		require(CLOSEBRACE, "Missing \\} for block",s);
		return node;
	}

	/**
	 * Report a failure in the parser.
	 */
	static void fail(String message, Scanner s) {
		StringBuilder msg = new StringBuilder(message + "\n   @ ...");
		for (int i = 0; i < 5 && s.hasNext(); i++) {
			msg.append(" ").append(s.next());
		}
		throw new ParserFailureException(msg + "...");
	}

	/**
	 * Requires that the next token matches a pattern if it matches, it consumes
	 * and returns the token, if not, it throws an exception with an error
	 * message
	 */
	static String require(String p, String message, Scanner s) {
		if (s.hasNext(p)) {
			return s.next();
		}
		fail(message, s);
		return null;
	}

	static String require(Pattern p, String message, Scanner s) {
		if (s.hasNext(p)) {
			return s.next();
		}
		fail(message, s);
		return null;
	}

	/**
	 * Requires that the next token matches a pattern (which should only match a
	 * number) if it matches, it consumes and returns the token as an integer if
	 * not, it throws an exception with an error message
	 */
	static int requireInt(String p, String message, Scanner s) {
		if (s.hasNext(p) && s.hasNextInt()) {
			return s.nextInt();
		}
		fail(message, s);
		return -1;
	}

	static int requireInt(Pattern p, String message, Scanner s) {
		if (s.hasNext(p) && s.hasNextInt()) {
			return s.nextInt();
		}
		fail(message, s);
		return -1;
	}

	/**
	 * Checks whether the next token in the scanner matches the specified
	 * pattern, if so, consumes the token and return true. Otherwise returns
	 * false without consuming anything.
	 */
	static boolean checkFor(String p, Scanner s) {
		if (s.hasNext(p)) {
			s.next();
			return true;
		} else {
			return false;
		}
	}

	static boolean checkFor(Pattern p, Scanner s) {
		if (s.hasNext(p)) {
			s.next();
			return true;
		} else {
			return false;
		}
	}
}

// You could add the node classes here, as long as they are not declared public (or private)

enum ACTION {
	turnL, turnR, wait, takeFuel, move, turnAround, shieldOn, shieldOff
}
enum SENSOR {
	fuelLeft, oppLR, oppFB, numBarrels, barrelLR, barrelFB, wallDist
}
enum COMP {
	lt, gt, eq
}
enum OP {
	add, mul, div, sub
}
enum CONDS{
	and, or, not
}

class statementNode implements RobotProgramNode{
	ArrayList<RobotProgramNode> nodes = new ArrayList<>();

	void addNode(RobotProgramNode node){
		nodes.add(node);
	}
	@Override
	public void execute(Robot robot) {
		nodes.forEach(n -> n.execute(robot));
	}
	public String toString(){
		StringBuilder ret = new StringBuilder();
		for(RobotProgramNode n : nodes){
			ret.append(n.toString());
		}
		return ret.toString();
	}
}

class programNode implements RobotProgramNode{
	ArrayList<RobotProgramNode> nodes = new ArrayList<>();

	void addNode(RobotProgramNode node){
		nodes.add(node);
	}

	@Override
	public void execute(Robot robot) {
		nodes.forEach(n -> n.execute(robot));
	}

	public String toString(){
		StringBuilder ret = new StringBuilder("(");
		for(RobotProgramNode n : nodes){
			ret.append(" + ").append(n.toString());
		}
		return ret + ")";
	}
}

class actNode implements RobotProgramNode{
	ACTION action;
	RobotSensorNode iter;

	public void setIterations(RobotSensorNode iter) {
		this.iter = iter;
	}

	public void setAction(ACTION action) {
		this.action = action;
	}

	@Override
	public void execute(Robot robot) {
		int iterations = (iter != null) ? iter.evaluate(robot) : 1;
		switch(action){
			case move:
				for(int i=0;i<iterations;i++){
					robot.move();
				}
				break;
			case turnL:
				robot.turnLeft();
				break;
			case turnR:
				robot.turnRight();
				break;
			case takeFuel:
				robot.takeFuel();
				break;
			case wait:
				for(int i=0;i<iterations;i++){
					robot.idleWait();
				}
				break;
			case turnAround:
				robot.turnAround();
				break;
			case shieldOn:
				robot.setShield(true);
				break;
			case shieldOff:
				robot.setShield(false);
				break;
		}
	}
	public String toString(){
		if(iter != null) return action.name() +"("+iter.toString()+")";
		return action.name();
	}
}

class loopNode implements RobotProgramNode{
	RobotProgramNode node;

	void addNode(RobotProgramNode node){
		this.node = node;
	}

	public String toString(){
		return "loop" + node.toString();
	}

	@Override
	public void execute(Robot robot) {
		while(true) node.execute(robot);
	}
}

class blockNode implements RobotProgramNode{
	ArrayList<RobotProgramNode> nodes = new ArrayList<>();

	public void addNode(RobotProgramNode node) {
		this.nodes.add(node);
	}
	public String toString(){
		StringBuilder ret = new StringBuilder("{\n");
		for(RobotProgramNode n : nodes){
			ret.append(" + ").append(n.toString());
		}
		return ret + "\n}";
	}
	@Override
	public void execute(Robot robot) {
		nodes.forEach(node -> node.execute(robot));
	}
}

class ifNode implements RobotProgramNode{
	RobotEvaluateNode condNode;
	RobotProgramNode node;
	RobotProgramNode elseNode;
	boolean b = false;
	ArrayList<ifNode> elif = new ArrayList<>();
	public String toString(){
		StringBuilder str = new StringBuilder("if(" + condNode.toString() + ")" + node.toString());
		if(!elif.isEmpty()) {
			for(ifNode n: elif){
				str.append(" el").append(n.toString());
			}
		}
		if(elseNode != null) str.append(" else ").append(elseNode.toString());
		return str.toString();
	}
	public boolean getB(){
		return b;
	}
	public void addCond(RobotEvaluateNode cond){
		this.condNode = cond;
	}
	public void addNode(RobotProgramNode node){
		this.node = node;
	}
	public void addElif(ifNode elif){
		this.elif.add(elif);
	}
	public void setElseNode(RobotProgramNode elseNode) {
		this.elseNode = elseNode;
	}

	@Override
	public void execute(Robot robot) {
		if(condNode.evaluate(robot)){
			b = true;
			node.execute(robot);
		}
		else if(!elif.isEmpty()) {
			for(ifNode n : elif){
				n.execute(robot);
				if(n.getB())break;
			}
		}
		else if(elseNode != null) elseNode.execute(robot);
	}
}

class whileNode implements RobotProgramNode{
	RobotEvaluateNode condNode;
	RobotProgramNode node;
	public String toString(){
		return "while(" + condNode.toString() + ")" + node.toString();
	}
	public void addCond(RobotEvaluateNode cond){
		this.condNode = cond;
	}
	public void addNode(RobotProgramNode node){
		this.node = node;
	}
	@Override
	public void execute(Robot robot) {
		while(condNode.evaluate(robot)) node.execute(robot);
	}
}

class conditionNode implements RobotEvaluateNode{
	COMP relop;
	CONDS cond;
	ArrayList<RobotEvaluateNode> conds = new ArrayList<>();
	RobotSensorNode sen;
	RobotSensorNode num;

	public void setCond(CONDS cond) {
		this.cond = cond;
	}
	public void addCond(RobotEvaluateNode condition){
		conds.add(condition);
	}
	public void setNum(RobotSensorNode num){
		this.num = num;
	}
	public void setSen(RobotSensorNode sen){
		this.sen = sen;
	}
	public void setRelop(COMP relop){
		this.relop = relop;
	}

	public String toString(){
		String retString;
		if(relop != null) retString = relop.toString() + "(" + sen.toString() +"," + num.toString() +")";
		else if(cond == CONDS.not) retString = cond.name() + "(" + conds.get(0).toString() +")";
		else retString = cond.name() + "(" + conds.get(0).toString() + "," + conds.get(1).toString() + ")";
		return retString;
	}

	@Override
	public boolean evaluate(Robot robot) {
		boolean b;
		if (relop == null){
			switch (cond){
				case or:
					b = conds.get(0).evaluate(robot) || conds.get(1).evaluate(robot);
					break;
				case and:
					b = conds.get(0).evaluate(robot) && conds.get(1).evaluate(robot);
					break;
				case not:
					b = !conds.get(0).evaluate(robot);
					break;
				default:
					throw new IllegalArgumentException();
			}
		}else {
			int number = num.evaluate(robot);
			int sensor = sen.evaluate(robot);
			switch (relop) {
				case eq:
					b = sensor == number;
					break;
				case gt:
					b = sensor > number;
					break;
				case lt:
					b = sensor < number;
					break;
				default:
					throw new IllegalArgumentException();
			}
		}
 		return b;
	}
}

class sensorNode implements RobotSensorNode{
	SENSOR sen;
	RobotSensorNode iter;

	public void setIter(RobotSensorNode iter) {
		this.iter = iter;
	}

	public void setSen(SENSOR sen) {
		this.sen = sen;
	}
	public String toString(){
		if(iter != null){
			return sen.toString()+"("+iter.toString()+")";
		}
		return sen.name();
	}

	@Override
	public int evaluate(Robot robot) {
		int iterations = (iter != null) ? iter.evaluate(robot) : 0;
		if(iterations > 13) iterations = 0;
		int i = Integer.MAX_VALUE;
		switch(sen){
			case oppFB:
				i = robot.getOpponentFB();
				break;
			case oppLR:
				i = robot.getOpponentLR();
				break;
			case barrelFB:
				i = robot.getBarrelFB(iterations);
				break;
			case barrelLR:
				i = robot.getBarrelLR(iterations);
				break;
			case fuelLeft:
				i = robot.getFuel();
				break;
			case wallDist:
				i = robot.getDistanceToWall();
				break;
			case numBarrels:
				i = robot.numBarrels();
				break;
		}
		return i;
	}
}

class numberNode implements RobotSensorNode{
	int number;
	public String toString(){
		return String.valueOf(number);
	}
	public void setNumber(int number) {
		this.number = number;
	}
	@Override
	public int evaluate(Robot robot) {
		return number;
	}
}

class expressionNode implements RobotSensorNode{
	ArrayList<RobotSensorNode> nodes = new ArrayList<>();
	OP operator;

	public void setOp(OP op) {
		this.operator = op;
	}

	public void addNode(RobotSensorNode node) {
		nodes.add(node);
	}

	public String toString(){
		if(nodes.size() > 1){
			 return operator.name() +"("+nodes.get(0).toString() +","+nodes.get(1).toString() +")";
		}
		return nodes.get(0).toString();
	}

	@Override
	public int evaluate(Robot robot) {
		int i;
		if(operator != null){
			switch (operator){
				case add:
					i = (nodes.get(0).evaluate(robot) + nodes.get(1).evaluate(robot));
					break;
				case sub:
					i = (nodes.get(0).evaluate(robot) - nodes.get(1).evaluate(robot));
					break;
				case mul:
					i = (nodes.get(0).evaluate(robot) * nodes.get(1).evaluate(robot));
					break;
				case div:
					i = (nodes.get(0).evaluate(robot) / nodes.get(1).evaluate(robot));
					break;
				default:
					throw new IllegalArgumentException();
			}
		}else return nodes.get(0).evaluate(robot);
		return i;
	}
}
class assignmentNode implements RobotProgramNode{
	variableNode variable;
	RobotSensorNode expression;

	public void setExpression(RobotSensorNode expression) {
		this.expression = expression;
	}
	public void setVariable(variableNode variable) {
		this.variable = variable;
	}
	public String toString(){
		return variable.toString() + "=" + expression.toString();
	}
	@Override
	public void execute(Robot robot) {
		Parser.variableMap.put(variable.getName(), expression.evaluate(robot));
	}
}
class variableNode implements RobotSensorNode{
	String variableName;

	public void setVariableName(String variableName) {
		this.variableName = variableName;
	}

	public String getName() {
		return variableName;
	}
	public String toString(){
		return this.getName();
	}

	@Override
	public int evaluate(Robot robot) {
		Parser.variableMap.putIfAbsent(this.getName(), 0);
		return Parser.variableMap.get(this.getName());
	}
}

