package grammar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import lexical.DFA;
import lexical.DFAFactory;
import lexical.Token;

public class AnalysisTableFactory {
	/**
	 * 该类从文件中读取文法，并生成LALR分析的分析表
	 */
	
	private static Symbol nilSymbol = new Symbol("nil", true); //空串符号
	
	/**
	 * 从文件中读取文法，并生成分析表
	 * @param grammarPath 文法文件路径
	 * @param dfa DFA实例
	 * @return LR分析表
	 */
	public static AnalysisTable creator(String grammarPath, DFA dfa) {
		List<Map<Symbol, Item>> table = new ArrayList<>(); //记录分析表，格式详情见AnalysisTable类
		ProductionList productions = new ProductionList(); //所有的产生式集
		ProjectSetList projectSetList = new ProjectSetList(); //所有的项目集(项目集列表)
		
		Map<String, Symbol> str2Symbol = new HashMap<>(); //记录String和Symbol的关系
		Map<Symbol, Set<Symbol>> firstSet = null; //记录每个非终结符对应的FIRST集
		
		Map<Token, Symbol> token2Symbol = new HashMap<>(); //记录Token和终结符的关系
		Set<Symbol> wrongHandling = new HashSet<>(); //恐慌模式恢复时选择的非终结符
		Map<Integer, Set<Symbol>> exceptOutlook = new HashMap<>(); //某个产生式不能有展望符的集合
		
		//文件的格式为:A->B c D /[](可选)
		File file = new File(grammarPath);
		try(FileReader fr = new FileReader(file);
			BufferedReader br = new BufferedReader(fr)) {
			String line = null;
			while((line=br.readLine()) != null) {
				if(line.length() == 0 || line.charAt(0) == '#') continue;	//文本中的注释和空行不读取
				int index = 0;
				if(line.charAt(line.length()-1) == '>') { //终结符对应Token序列
					index = line.indexOf(':');
					String symbolStr = line.substring(0, index);
					Token token = new Token(line.substring(index+1));
					token2Symbol.put(token, str2Symbol.get(symbolStr));
				} else if((index=line.indexOf("->")) != -1){
					String left = line.substring(0, index);
					Symbol leftSymbol = str2Symbol.get(left);
					if(leftSymbol == null) { //第一次读到该非终结符的产生式
						leftSymbol = new Symbol(left, false);
						str2Symbol.put(left, leftSymbol);
					} else if(leftSymbol.isFinal() == true) { //被错误地设置成了终结符
						leftSymbol.setIsFinal(false);
					}
					
					String rightStr = line.substring(index+2).trim();
					
					int exceptIndex = -1;
					Set<Symbol> exceptSymbolSet = null;
					if((exceptIndex=rightStr.indexOf('/')) != -1) {
						exceptSymbolSet = new HashSet<>();
						for(String s : rightStr.substring(exceptIndex+2, rightStr.length()-1).split(" ")) { //所有不被包含的展望符
							Symbol exceptSymbol = str2Symbol.get(s);
							if(exceptSymbol == null) {
								exceptSymbol = new Symbol(s, true);
								str2Symbol.put(s, exceptSymbol);
							}
							exceptSymbolSet.add(exceptSymbol);
						}
						rightStr = rightStr.substring(0, exceptIndex-1).trim();
					}
					
					List<Symbol> rightSymbols = new ArrayList<>();
					String right[] = rightStr.split(" ");
						
					for(String s : right) {
						Symbol rightSymbol = str2Symbol.get(s);
						if(rightSymbol == null) {
							rightSymbol = new Symbol(s, true);
							str2Symbol.put(s, rightSymbol);
						}
						rightSymbols.add(rightSymbol);
					}
					if(rightSymbols.get(0).getName().equals("nil")) //空产生式
						rightSymbols = null;
					Production newProduction = new Production(leftSymbol, rightSymbols);
					if(exceptIndex != -1) { //有不允许的展望符
						exceptOutlook.put(productions.productions.size(), exceptSymbolSet);
					}
					productions.add(newProduction);
				} else { //错误处理
					wrongHandling.add(str2Symbol.get(line.substring(line.indexOf(' ')+1)));
				}
			}
		} catch(IOException e) {
			e.printStackTrace();
		}
		productions.addAllSymbols(str2Symbol.values());
		productions.addSymbols(new Symbol("$", true));
		
//		System.out.println(str2Symbol);
		firstSet = FirstSet(productions);
		
		Set<Symbol> startLookOut = new HashSet<>();
		startLookOut.add(new Symbol("$", true));
		Project startProject = new Project(productions.productions.get(0).leftSymbol, productions.productions.get(0).rightSymbols, startLookOut, 0, 0);
		ProjectSet startProjectSet = new ProjectSet(0);
		startProjectSet.add(startProject);
		startProjectSet = CLOSURE(firstSet, productions, startProjectSet, 0);
		projectSetList.add(startProjectSet);
		int projectSetIndex = 1;
		
		Map<Integer, Map<Symbol, Integer>> GOTOtable = new HashMap<>(); //存储每个项目集之间的跳转
		Map<String, Integer> projectSetStr2Index = new HashMap<>(); //记录每个项目集在projectSetList中的编号
		Set<String> projectSetsStr = new HashSet<>(); //用来去重的项目集，与projectSetList等价
		projectSetsStr.add(startProjectSet.toString());
		
		Queue<ProjectSet> queue = new LinkedList<>();
		projectSetStr2Index.put(startProjectSet.toString(), 0);
		queue.offer(startProjectSet);
		table.add(new HashMap<>());
		
		while(!queue.isEmpty()) {
			ProjectSet I = queue.poll(); //C中的每个项集I
			for(Symbol X : I.canGoSymbols()) { //每个文法符号X
				ProjectSet nextProjectSet = GOTO(firstSet, productions, I, X, exceptOutlook, projectSetIndex); //形成一个新的后继项目集
				
				if(!projectSetsStr.contains(nextProjectSet.toString())) { //GOTO(I, X)非空且不在C中
					projectSetList.add(nextProjectSet);
					projectSetsStr.add(nextProjectSet.toString());
					queue.offer(nextProjectSet);
					projectSetStr2Index.put(nextProjectSet.toString(), projectSetIndex);
					projectSetIndex++; //项目集编号自增
					table.add(new HashMap<>());
				} else {
					nextProjectSet = projectSetList.projectSets.get(projectSetStr2Index.get(nextProjectSet.toString()));
				}
				if(GOTOtable.containsKey(I.index)) { //跳转表中有I了
					GOTOtable.get(I.index).put(X, nextProjectSet.index);
				} else { //跳转表中还没I
					Map<Symbol, Integer> mapTemp = new HashMap<>();
					mapTemp.put(X, nextProjectSet.index);
					GOTOtable.put(I.index, mapTemp);
				}
			}
		}
		
//		StringBuilder conflict = new StringBuilder(); //记录有冲突的表项，debug时用
		
		boolean isFindFinalStatus = false; //是否找到了移入$能够接收
		for(int i=0; i<table.size(); i++) {
			if(GOTOtable.get(i) == null) { //没有后继状态
				if(!isFindFinalStatus &&
				   projectSetList.projectSets.get(i).isAcc(startProject)) { //为接收时赋值
					isFindFinalStatus = true;
					Item item = new Item(null, -1);
					table.get(i).put(new Symbol("$", true), item);
				} else {
					for(Project p : projectSetList.projectSets.get(i).projects) { //看看项目集中有没有可归约的项目集
						if(p.isReduce) { //可归约的项目
							for(Symbol outlook : p.outlook) { //把每一个展望符加入到表中
//								if(table.get(i).get(outlook) != null) conflict.append("I"+i+"  ");
								Item item = new Item(ItemType.REDUCE, p.productionIndex);
								table.get(i).put(outlook, item);
							}
						}
					}
				}
				continue;
			}
			Set<Symbol> canGo = GOTOtable.get(i).keySet(); //从这个状态集能够接受啥样的符号
			for(Project p : projectSetList.projectSets.get(i).projects) { //看看项目集中有没有可归约的项目集
				if(p.isReduce) { //可归约的项目
					for(Symbol outlook : p.outlook) { //把每一个展望符加入到表中
//						if(table.get(i).get(outlook) != null) conflict.append("I"+i+"  ");
						Item item = new Item(ItemType.REDUCE, p.productionIndex);
						table.get(i).put(outlook, item);
					}
				}
			}
			for(Symbol s : canGo) {
				if(table.get(i).get(s) != null) { //已经有规约动作了，规约的优先级比移入高
//					conflict.append("I"+i+":  ");
					continue;
				}
				Item item = null;
				if(s.isFinal()) { //该符号是终结符
					item = new Item(ItemType.SHIFT, GOTOtable.get(i).get(s));
				} else { //该符号是非终结符
					item = new Item(ItemType.GOTO, GOTOtable.get(i).get(s));
				}
				table.get(i).put(s, item);
			}
		}
		
//		StringBuilder result = new StringBuilder();
//		result.append("冲突："+conflict+"\n");
//		result.append("\n产生式：\n" + productions+"\n");
//		result.append("\n项目集\n"+projectSetList+"\n");
//		result.append("\n跳转表：");
//		for(int i=0; i<table.size(); i++) {
//			result.append("  I"+i+":\n");
//			for(Symbol s : table.get(i).keySet()) {
//				result.append(s+": "+table.get(i).get(s)+"\n");
//			}
//			result.append("\n");
//		}
//		File temp = new File("result.txt");
//		try(BufferedWriter bw = new BufferedWriter(new FileWriter(temp))) {
//			bw.write(result.toString());
//		} catch(IOException e) {
//			e.printStackTrace();
//		}
		
		return new AnalysisTable(table, productions, projectSetList, token2Symbol, wrongHandling, dfa);
	}
	
	/**
	 * 根据文法和DFA构造LR分析表
	 * @param grammarPath 文法文件路径
	 * @param faPath FA文件路径
	 * @return LR分析表
	 */
	public static AnalysisTable creator(String grammarPath, String faPath) {
		DFA dfa;
		if(faPath.charAt(faPath.length()-3) == 'n') { //NFA
			dfa = DFAFactory.creatorUseNFA(faPath);
		} else { //DFA
			dfa = DFAFactory.creator(faPath);
		}
	
		return creator(grammarPath, dfa);
	}
	
	/**
	 * 计算所有符号的FIRST集
	 * @param productions 所有的产生式集合
	 * @return 所有符号的FIRST集
	 */
	private static Map<Symbol, Set<Symbol>> FirstSet(ProductionList productions) {
		Map<Symbol, Set<Symbol>> firstMap = new HashMap<>();
		Set<Production> set = new HashSet<>(); //右部以非终结符打头且无左递归的产生式
		
		for(Production p : productions.productions) {
			if(!firstMap.containsKey(p.leftSymbol)) {
				firstMap.put(p.leftSymbol, new HashSet<>());
			}
			if(p.isNil) { //空产生式
				firstMap.get(p.leftSymbol).add(nilSymbol);
			} else if(p.rightSymbols.get(0).isFinal()) { //右部以终结符打头
				firstMap.get(p.leftSymbol).add(p.rightSymbols.get(0));
			} else/* if(!p.leftSymbol.getName().equals(p.rightSymbols.get(0).getName()))*/{
				set.add(p);
			}
		}

		boolean flag = true;
		while(flag) {
			flag = false;
			for(Production p : set) {
				for(int i=0; i<p.rightSymbols.size(); i++) {
					Set<Symbol> leftSymbolFirst = firstMap.get(p.leftSymbol);
					Symbol s = p.rightSymbols.get(i);
					if(s.isFinal()) { //有终结符，就相当于已经求到了
						if(!flag) {
							flag = firstMap.get(p.leftSymbol).add(s);
						} else {
							leftSymbolFirst.addAll(firstMap.get(s)); //直到FIRST集不再发生变化
						}
						break;
					} else if(firstMap.get(s).size() > 0) { //s已经求出了FIRST集
						Set<Symbol> sFirst = firstMap.get(s);
						if(i == p.rightSymbols.size()-1) { //当前符号是最后一个符号
							if(!flag) {
								flag = firstMap.get(p.leftSymbol).addAll(firstMap.get(s));
							} else {
								leftSymbolFirst.addAll(firstMap.get(s)); //直到FIRST集不再发生变化
							}
							break;
						}
						if(!sFirst.contains(nilSymbol)) { //当前符号没有空的FIRST集且不是最后一个符号
							if(!flag) {
								flag = firstMap.get(p.leftSymbol).addAll(firstMap.get(s));
							} else {
								leftSymbolFirst.addAll(firstMap.get(s)); //直到FIRST集不再发生变化
							}
							break;
						} else { //当前符号有空的FIRST集且不是最后一个符号
							if(s.getName().equals(p.leftSymbol.getName()))
								continue;
							sFirst.remove(nilSymbol);
							if(!flag) {
								flag = firstMap.get(p.leftSymbol).addAll(firstMap.get(s));
							} else {
								leftSymbolFirst.addAll(firstMap.get(s)); //直到FIRST集不再发生变化
							}
							sFirst.add(nilSymbol);
						}
					} else { //这个产生式的第i位的FIRST集还没求出来，所以就跳过这个产生式先
						break;
					}
				}
			}
		}
		
		//打印FIRST集
//		for(Symbol s : firstMap.keySet())
//			System.out.println("FIRST("+s+") = "+firstMap.get(s));
		
		return firstMap;
	}
	
	/**
	 * 计算符号串的FIRST集
	 * @param firstSet 已经计算好的所有单个符号的FIRST集
	 * @param symbols 需要计算FIRST集的符号串
	 * @return 所有符号的FIRST集
	 */
	private static Set<Symbol> First(Map<Symbol, Set<Symbol>> firstSet, List<Symbol> symbols) {
		Set<Symbol> result = new HashSet<>();

		for(Symbol s : symbols) {
			Set<Symbol> temp = firstSet.get(s);
			if(temp == null) { //s是终结符
				result.add(s);
				break;
			}
			result.addAll(temp);
			if(!temp.contains(nilSymbol))
				break;
		}
		
		return result;
	}
	
	/**
	 * 求项目I的闭包
	 * @param firstSet 所有非终结符的FIRST集
	 * @param productions 产生式集合
	 * @param I 需要求闭包的项目集
	 * @param index 该项目集的编号
	 * @return 经过合并后的项目集I的闭包
	 */
	private static ProjectSet CLOSURE(Map<Symbol, Set<Symbol>> firstSet, ProductionList productions, ProjectSet I, int index) {
		ProjectSet result = new ProjectSet(index);
		Queue<Project> queue = new LinkedList<>();
		
		result.addAll(I.projects);
		queue.addAll(I.projects);

		while(!queue.isEmpty()) {
			Project A = queue.poll();
			if(A.isReduce) continue; //规约项目不会有
			//I中的每个项[A->α·Bβ, a]
			List<Symbol> ABehindPosSymbols = A.getBehindPosSymbols();
			List<Symbol> list = new ArrayList<>(); //βa
			Set<Symbol> FIRST;//FIRST(βa)
			if(ABehindPosSymbols != null) {
				list.addAll(ABehindPosSymbols);
				FIRST = First(firstSet, list);
			} else {
				FIRST = new HashSet<>();
			}
			if(FIRST.size() == 0 || FIRST.contains(nilSymbol)) {
				FIRST.remove(nilSymbol);
				FIRST.addAll(A.outlook);
			}
			Symbol B = A.getPosSymbol();
			if(!B.isFinal()) { //非终结符
				for(Production Bproduction : productions.symbol2Production(B)) { //G'的每个产生式B->γ
					for(Symbol b : FIRST) { //FIRST(βa)中的每个符号b
						Set<Symbol> outlook = new HashSet<>();
						outlook.add(b);
						Project newProject = new Project(B, Bproduction.rightSymbols, outlook, 0, Bproduction.index);
						if(result.add(newProject)) { //将[B->·γ, b]加入到集合result中
							queue.offer(newProject);
						}
					}
				}
			}
		}
		result.merge();
		
		return result;
	}
	
	/**
	 * 某个项目集I输入符号X后的后继项目集
	 * @param firstSet 所有非终结符的FIRST集
	 * @param productions 所有产生式的列表
	 * @param I 跳转前项目集
	 * @param X 输入符号X
	 * @param exceptOutlook 不被允许的展望符
	 * @param index 跳转后的项目集编号index
	 * @return 跳转后项目集
	 */
	public static ProjectSet GOTO(Map<Symbol, Set<Symbol>> firstSet, ProductionList productions, ProjectSet I, Symbol X, Map<Integer, Set<Symbol>> exceptOutlook, int index) {
		ProjectSet result = new ProjectSet(index);
		for(Project p : I.projects) {
			if(p.isReduce) { //规约项目，没有后续项目集，且去除不允许的展望符
				if(exceptOutlook.get(p.productionIndex) != null) {
					p.outlook.removeAll(exceptOutlook.get(p.productionIndex));
				}
				continue;
			} 
			if(!p.getPosSymbol().equals(X)) { //·后的符号不是X
				continue;
			}
			result.add(new Project(p.leftSymbol, p.production, p.outlook, p.pos+1, p.productionIndex));
		}
		
		result = CLOSURE(firstSet, productions, result, index);
		
		return result;
	}
	
	public static void main(String[] args) {
		creator("grammar.txt", "NFA.nfa");
	}
}