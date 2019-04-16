package grammar;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import lexical.Token;

public class GrammarTree {
	/**
	 * 作为符号栈中的符号
	 * 每个符号有以下属性
	 * symbol 它对应的符号
	 * token 它对应的token，若是非终结符则为null
	 * lineNumber 对应的行号
	 * child 它的孩子符号
	 * isVisited 用来递归遍历的时候作为是否访问过的标记
	 */
	
	public Symbol symbol;
	public Token token;
	public int lineNumber;
	public List<GrammarTree> children;
	public boolean isVisited = false;
	
	public GrammarTree(Symbol symbol, Token token) {
		this.symbol = symbol;
		this.token = token;
		if(token == null) lineNumber = -1;
		else this.lineNumber = token.getLineNumber();
		this.children = null;
	}
	
	public Symbol getSymbol() {
		return symbol;
	}
	
	public int lineNumber() {
		return lineNumber;
	}
	
	public void addChild(GrammarTree child) {
		if(children == null) {
			children = new ArrayList<>();
		}
		if(child.lineNumber != -1) { //父节点符号的行号为第一个非空产生式的子节点的行号
			this.lineNumber = child.lineNumber;
		}
		children.add(child);
	}
	
	public String getResultString() {
		StringBuffer sb = new StringBuffer();
		Stack<GrammarTree> stack = new Stack<>();
		stack.push(this);
		StringBuffer tab = new StringBuffer();
		
		while(!stack.isEmpty()) {
			GrammarTree top = stack.peek();
			if(top.isVisited) { //子树已经压入栈中
				stack.pop();
				top.isVisited = false; //重置
				tab.delete(tab.length()-2, tab.length());
			} else { //子树还没压入栈
				if(top.children == null) { //终结符或者是空产生式
					if(top.symbol.isFinal()) { //终结符
						sb.append(tab.toString()+top.symbol.getName()+"("+top.lineNumber+"):"+top.token.forGrammar()+"\n");
						top.isVisited = false;
					} else { //空产生式
						sb.append(tab.toString()+top.symbol.getName()+"("+top.lineNumber+")\n");
						sb.append(tab.toString()+"  nil\n");
						top.isVisited = false;
					}
					stack.pop();
				} else { //非空产生式
					sb.append(tab.toString()+top.symbol.getName()+"("+top.lineNumber+")\n");
					tab.append("  ");
					top.isVisited = true;
					for(GrammarTree st : top.children) { //展开子树
						stack.push(st);
					}
				}
			}
		}
		sb.delete(sb.length()-1, sb.length());//去掉最后的换行
		return sb.toString();
	}
	
	@Override
	public String toString() {
		return symbol.toString();
	}
}