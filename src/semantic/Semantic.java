package semantic;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import grammar.GrammarTree;

public class Semantic {
	/**
	 * 该类是一个语义分析工具类
	 * 
	 */
	private static Stack<SymbolTable> stack;
	private static SymbolTable curSymbolTable;
	
	private static StringBuilder error;
	
	private static List<Code> codes; //三地址码
	
	/* 将类型和宽度信息从语法分析树中的B节点传递到对应于产生式C->nil的结点 */
	private static String t;
	private static int w;
	
	/* 作为生成新的临时变量的下标 */
	private static int temp;
	
	/* 作为生成新的指令标号的下标 */
	private static int quadIndex;
	
	/**
	 * 语义分析初始化
	 */
	public static void init() {
		stack = new Stack<>();
		SymbolTable mainTable = new SymbolTable("main");
		stack.push(mainTable);
		curSymbolTable = mainTable;
		error = new StringBuilder();
		codes = new ArrayList<>();
		temp = 1;
		quadIndex = 1;
	}
	
	
	@SuppressWarnings("unchecked")
	public static void setProperty(GrammarTree gt) {
		//记得每个节点的孩子是从右往左存储的
		switch(gt.productionIndex) {
		case 0: //P'->P
			break;
		case 1: //P->DP
			break;
		case 2: //P->SP
			break;
		case 3: //P->nil
			break;
		case 4: //D->Dv
			break;
		case 5: //Dv'->DvDv'
			break;
		case 6: //Dv'->nil
			break;
		case 7: //D->procid(Dv'){P}
			break;
		case 8: //D->procid(){P}
			break;
		case 9: //D->recordid{Dv'}
			break;
		case 10: { //Dv->Tid;
			String idName = (String)gt.children.get(1).token.getValue();
			String Ttype = (String)gt.children.get(2).property.get("type");
			int Tspace = (int)gt.children.get(2).property.get("width");
			curSymbolTable.enter(idName, Ttype, Tspace, null);
			if(Ttype.charAt(Ttype.length()-1) == ')') {
				Object idvalue;
				if(Ttype.charAt(Ttype.lastIndexOf(',')+2) == 'i') { //int数组
					idvalue = new int[Tspace/4];
				} else { //float数组
					idvalue = new float[Tspace/4];
				}
				curSymbolTable.setValue(idName, idvalue);
			}
		}break;
		case 11: { //T->XC'
			String C_type = (String)gt.children.get(0).property.get("type");
			int C_width = (int)gt.children.get(0).property.get("width");
			gt.property.put("type", C_type);
			gt.property.put("width", C_width);
		}break;
		case 12: { //T->X
			String Btype = (String)gt.children.get(0).property.get("type");
			int Bwidth = (int)gt.children.get(0).property.get("width");
			gt.property.put("type", Btype);
			gt.property.put("width", Bwidth);
		}break;
		case 13: { //X->int
			t = "int";
			w = 4;
			gt.property.put("type", "int");
			gt.property.put("width", 4);
		}break;
		case 14: { //X->float
			t = "float";
			w = 4;
			gt.property.put("type", "float");
			gt.property.put("width", 4);
		}break;
		case 15: { //C'->CC'
			int numVal = (int)gt.children.get(1).property.get("num");
			String C1_type = (String)gt.children.get(0).property.get("type");
			int C1_width = (int)gt.children.get(0).property.get("width");
			String C0_type = "array("+numVal+", "+C1_type+")";
			gt.property.put("type", C0_type);
			gt.property.put("width", numVal*C1_width);
		}break;
		case 16: { //C'->nil
			gt.property.put("type", t);
			gt.property.put("width", w);
		}break;
		case 17: { //C->[number]
			gt.property.put("num", Integer.valueOf(gt.children.get(1).token.getValue()));
		}break;
		case 18: { //S->id=E;
			String idName = gt.children.get(3).token.getValue();
			Row p = curSymbolTable.lookUp(idName);
			Map<String, Object> Eproperty = gt.children.get(1).property;
			String Etype = (String)Eproperty.get("type");
			String Ename = (String)Eproperty.get("name");
			Object Evalue = Eproperty.get("value");
			if(p == null) { //变量未经声明就使用
				error.append("Error at Line ["+gt.lineNumber+"]: 变量"+idName+"未经声明就使用\n");
				break;
			}
			Object idvalue;
			if(!p.getType().equals(Etype)) { //两者类型不一样
				if(p.getType().equals("int")) { //id是int，表达式右边是float，则无法强转，出错
					//TODO:无法强转
					break;
				} else { //id是float，表达式右边是int，则可以强转
					idvalue = (float)((int)Evalue);
				}
			} else { //类型一样
				idvalue = Evalue;
			}
			p.setValue(idvalue);
			Assign assign = new Assign(p.getVarName(), Ename);
			codes.add(assign);
		}break;
		case 19: { //E->E+E
			Map<String, Object> E1property = gt.children.get(2).property;
			Map<String, Object> E2property = gt.children.get(0).property;
			String E1type = (String)E1property.get("type");
			String E1name = (String)E1property.get("name");
			Object E1value = E1property.get("value");
			String E2type = (String)E2property.get("type");
			String E2name = (String)E2property.get("name");
			Object E2value = E2property.get("value");
			String E0type;
			Object E0value;
			if(E1type.equals(E2type)) { //同类型计算
				E0type = E1type;
				if(E1type.equals("int")) { //都是int
					E0value = (int)((int)E1value*(int)E2value);
				} else { //都是float
					E0value = (float)((float)E1value+(float)E2value);
				}
			} else if(E1type.equals("int")) { //E1是int，E2是float
				E0type = E2type;
				E0value = (float)((int)E1value+(float)E2value);
			} else { //E1是float，E2是int
				E0type = E1type;
				E0value = (float)((float)E1value*(int)E2value);
			}
			String t_ = newTemp();
			gt.property.put("type", E0type);
			gt.property.put("name", t_);
			gt.property.put("value", E0value);
			Assign assign = new Assign('+', t_, E1name, E2name);
			codes.add(assign);
		}break;
		case 20: { //E->E*E
			Map<String, Object> E1property = gt.children.get(2).property;
			Map<String, Object> E2property = gt.children.get(0).property;
			String E1type = (String)E1property.get("type");
			String E1name = (String)E1property.get("name");
			Object E1value = E1property.get("value");
			String E2type = (String)E2property.get("type");
			String E2name = (String)E2property.get("name");
			Object E2value = E2property.get("value");
			String E0type;
			Object E0value;
			if(E1type.equals(E2type)) { //同类型计算
				E0type = E1type;
				if(E1type.equals("int")) { //都是int
					E0value = (int)((int)E1value*(int)E2value);
				} else { //都是float
					E0value = (float)((float)E1value*(float)E2value);
				}
			} else if(E1type.equals("int")) { //E1是int，E2是float
				E0type = E2type;
				E0value = (float)((int)E1value*(float)E2value);
			} else { //E1是float，E2是int
				E0type = E1type;
				E0value = (float)((float)E1value*(int)E2value);
			}
			String t_ = newTemp();
			gt.property.put("type", E0type);
			gt.property.put("name", t_);
			gt.property.put("value", E0value);
			Assign assign = new Assign('*', t_, E1name, E2name);
			codes.add(assign);
		}break;
		case 21: { //E->-E
			String E1type = (String)gt.children.get(0).property.get("type");
			String E1name = (String)gt.children.get(0).property.get("name");
			Object E0value;
			if(E1type.equals("int")) {
				E0value = -(int)gt.children.get(0).property.get("value");
			} else if(E1type.equals("float")) {
				E0value = -(float)gt.children.get(0).property.get("value");
			} else {
				//TODO:错误处理
				break;
			}
			String tempStr = newTemp();
			gt.property.put("name", tempStr);
			gt.property.put("value", E0value);
			gt.property.put("type", E1type);
			Assign assign = new Assign('-', tempStr, E1name);
			codes.add(assign);
		}break;
		case 22: { //E->(E)
			gt.property = gt.children.get(1).property;
		}break;
		case 23: { //E->id
			String idName = gt.children.get(0).token.getValue();
			Row p = curSymbolTable.lookUp(idName);
			if(p == null) { //变量未经声明就使用
				error.append("Error at Line ["+gt.lineNumber+"]: 变量"+idName+"未经声明就使用\n");
				break;
			}
			gt.property.put("name", idName);
			gt.property.put("value", p.getValue());
			gt.property.put("type", p.getType());
		}break;
		case 24: { //E->int_num
			String Ename = newTemp();
			int Evalue = (int)Integer.valueOf(gt.children.get(0).token.getValue());
			gt.property.put("name", Ename);
			gt.property.put("value", Evalue);
			gt.property.put("type", "int");
			Assign assign = new Assign(Ename, String.valueOf(Evalue));
			codes.add(assign);
		}break;
		case 25: { //E->float_num
			String Ename = newTemp();
			float Evalue = (float)Integer.valueOf(gt.children.get(0).token.getValue());
			gt.property.put("name", Ename);
			gt.property.put("value", Evalue);
			gt.property.put("type", "float");
			Assign assign = new Assign(Ename, String.valueOf(Evalue));
			codes.add(assign);
		}break;
		case 26: { //E->L
			String t_ = newTemp();
			int Loffset = (int)gt.children.get(0).property.get("offset");
			gt.property.put("name", t_);
			Row array = curSymbolTable.lookUp((String)gt.children.get(0).property.get("array"));
			gt.property.put("type", array.getType());
			gt.property.put("value", ((int[])array.getValue())[Loffset/4]);
			Assign assign = new Assign(t_, array.getVarName(), (String)gt.children.get(0).property.get("offsetName"));
			codes.add(assign);
		}break;
		case 27: { //L->id[E]
			String idName = gt.children.get(3).token.getValue();
			Row p = curSymbolTable.lookUp(idName);
			Map<String, Object> Eproperty = gt.children.get(1).property;
			String Etype = (String)Eproperty.get("type");
			if(p == null) { //变量未经声明就使用
				error.append("Error at Line ["+gt.lineNumber+"]: 变量"+idName+"未经声明就使用\n");
				break;
			} else if(!Etype.equals("int")) {
				//TODO:数组中的维数不是整数
				break;
			}
			String Ltype = p.getType().substring(p.getType().indexOf(' ')+1, p.getType().length()-1);
			int Loffset = 4 * (int)Eproperty.get("value");
			// 以下计算offset
			int index = -1;
			String temp = Ltype;
			while((index=temp.indexOf(',')) != -1) {
				Loffset = Loffset * Integer.valueOf(temp.substring(6, index));
				temp = temp.substring(index+2, temp.length()-1);
			}
			gt.property.put("offset", Loffset);
			gt.property.put("array", idName);
			gt.property.put("type", Ltype);
			String t_ = newTemp();
			Assign assign = new Assign('*', t_, (String)Eproperty.get("name"), String.valueOf(Loffset));
			codes.add(assign);
			gt.property.put("offsetName", t_);
		}break;
		case 28: { //L->L[E]
			Map<String, Object> L1property = gt.children.get(3).property;
			Map<String, Object> Eproperty = gt.children.get(1).property;
			String Etype = (String)Eproperty.get("type");
			if(!Etype.equals("int")) {
				//TODO:数组中的数字不是整数
				break;
			}
			String L1type = (String)L1property.get("type");
			String L0type = L1type.substring(L1type.indexOf(' ')+1, L1type.length()-1);
			int L0offset = 4;
			// 以下计算offset
			int index = -1;
			String temp = L0type;
			while((index=temp.indexOf(',')) != -1) {
				L0offset = L0offset * Integer.valueOf(temp.substring(6, index));
				temp = temp.substring(index+2, temp.length()-1);
			}
			
			gt.property.put("array", (String)L1property.get("array"));
			gt.property.put("type", L0type);
			String t_1 = newTemp();
			String t_2 = newTemp();
			Assign assign1 = new Assign('*', t_1, (String)gt.children.get(1).property.get("name"), String.valueOf(L0offset));
			Assign assign2 = new Assign('+', t_2, (String)L1property.get("offsetName"), t_1);
			codes.add(assign1);
			codes.add(assign2);
			L0offset = L0offset * (int)Eproperty.get("value") + (int)L1property.get("offset");
			gt.property.put("offset", L0offset);
			gt.property.put("offsetName", t_2);
		}break;
		case 29: { //S->L=E;
			Map<String, Object> Eproperty = gt.children.get(1).property;
			Map<String, Object> Lproperty = gt.children.get(3).property;
			String Etype = (String)Eproperty.get("type");
			String Ename = (String)Eproperty.get("name");
			Object Evalue = Eproperty.get("value");
			String Ltype = (String)Lproperty.get("type");
			String Larray = (String)Lproperty.get("array");
			int Loffset = (int)Lproperty.get("offset");
			Row array = curSymbolTable.lookUp(Larray);
			if(Etype.equals(Ltype)) { //相同类型
				if(Ltype.equals("int")) { //都是int
					int[] L = (int[])array.getValue();
					L[Loffset/4] = (int)Evalue;
				} else { //都是float
					float[] L = (float[])array.getValue();
					L[Loffset/4] = (float)Evalue;
				}
			} else if(Ltype.equals("int")) { //数组是int，表达式是float
				//TODO:强转失败
				break;
			} else if(Ltype.equals("float")) { //数组是float，表达式是int
				float[] L = (float[])array.getValue();
				L[Loffset/4] = (float)((int)Evalue);
			} else {
				//TODO:错误处理，没到数组最后一维
				break;
			}
			ArrayAssign arrayAssign = new ArrayAssign(Larray, (String)Lproperty.get("offsetName"), Ename);
			codes.add(arrayAssign);
		}break;
		case 30: { //S'->SMS'
			List<Integer> S2nextList = (List<Integer>)gt.children.get(0).property.get("nextList");
			List<Integer> S1nextList = (List<Integer>)gt.children.get(2).property.get("nextList");
			int Mquad = (int)gt.children.get(1).property.get("quad");
			backPatch(S1nextList, Mquad);
			gt.property.put("nextList", S2nextList);
		}break;
		case 31: //S'->nil
			break;
		case 32: { //S->ifMBthen{MS'}
			Map<String, Object> Bproperty = gt.children.get(6).property;
			List<Integer> SnextList = new ArrayList<>();
			SnextList.addAll((List<Integer>)Bproperty.get("falseList"));
			SnextList.addAll((List<Integer>)gt.children.get(1).property.get("nextList"));
			backPatch((List<Integer>)Bproperty.get("trueList"), (int)gt.children.get(2).property.get("quad"));
			gt.property.put("nextList", SnextList);
		}break;
		case 33: { //S->ifMBthen{MS'N}else{MS'}
			Map<String, Object> Bproperty = gt.children.get(11).property;
			List<Integer> SnextList = new ArrayList<>();
			SnextList.addAll((List<Integer>)gt.children.get(7).property.get("nextList"));
			SnextList.addAll((List<Integer>)gt.children.get(6).property.get("nextList"));
			SnextList.addAll((List<Integer>)gt.children.get(1).property.get("nextList"));
			backPatch((List<Integer>)Bproperty.get("trueList"), (int)gt.children.get(8).property.get("quad"));
			backPatch((List<Integer>)Bproperty.get("falseList"), (int)gt.children.get(2).property.get("quad"));
			gt.property.put("nextList", SnextList);
		}break;
		case 34: { //S->whileMBdo{MS'}
			Map<String, Object> Bproperty = gt.children.get(5).property;
			List<Integer> BfalseList = (List<Integer>)Bproperty.get("falseList");
			List<Integer> BtrueList = (List<Integer>)Bproperty.get("trueList");
			List<Integer> S1nextList = (List<Integer>)gt.children.get(1).property.get("nextList");
			int M1quad = (int)gt.children.get(6).property.get("quad");
			int M2quad = (int)gt.children.get(2).property.get("quad");
			List<Integer> SnextList = BfalseList;
			backPatch(S1nextList, M1quad);
			backPatch(BtrueList, M2quad);
			gt.property.put("nextList", SnextList);
			BoolExpression boolExpression = new BoolExpression();
			boolExpression.setQuad(M1quad);
			codes.add(boolExpression);
		}break;
		case 35: { //B->BorMB
			Map<String, Object> B1property = gt.children.get(3).property;
			Map<String, Object> B2property = gt.children.get(0).property;
			List<Integer> B1trueList = (List<Integer>)B1property.get("trueList");
			List<Integer> B1falseList = (List<Integer>)B1property.get("falseList");
			List<Integer> B2trueList = (List<Integer>)B2property.get("trueList");
			List<Integer> B2falseList = (List<Integer>)B2property.get("falseList");
			List<Integer> BtrueList = new ArrayList<>();
			BtrueList.addAll(B1trueList);
			BtrueList.addAll(B2trueList);
			List<Integer> BfalseList = B2falseList;
			int Mquad = (int)gt.children.get(1).property.get("quad");
			backPatch(B1falseList, Mquad);
			gt.property.put("trueList", BtrueList);
			gt.property.put("falseList", BfalseList);
		}break;
		case 36: { //B->BandMB
			Map<String, Object> B1property = gt.children.get(3).property;
			Map<String, Object> B2property = gt.children.get(0).property;
			List<Integer> B1trueList = (List<Integer>)B1property.get("trueList");
			List<Integer> B1falseList = (List<Integer>)B1property.get("falseList");
			List<Integer> B2trueList = (List<Integer>)B2property.get("trueList");
			List<Integer> B2falseList = (List<Integer>)B2property.get("falseList");
			List<Integer> BtrueList = B2trueList;
			List<Integer> BfalseList = new ArrayList<>();
			BfalseList.addAll(B1falseList);
			BfalseList.addAll(B2falseList);
			int Mquad = (int)gt.children.get(1).property.get("quad");
			backPatch(B1trueList, Mquad);
			gt.property.put("trueList", BtrueList);
			gt.property.put("falseList", BfalseList);
		}break;
		case 37: { //B->notB
			Map<String, Object> B1property = gt.children.get(2).property;
			List<Integer> B1trueList = (List<Integer>)B1property.get("trueList");
			List<Integer> B1falseList = (List<Integer>)B1property.get("falseList");
			gt.property.put("trueList", B1falseList);
			gt.property.put("falseList", B1trueList);
		}break;
		case 38: { //B->(B)
			gt.property = gt.children.get(1).property;
		}break;
		case 39: { //B->E<E
			int trueQuad = nextQuad();
			int falseQuad = nextQuad()+1;
			String condition = gt.children.get(2).property.get("name")+" < "+gt.children.get(0).property.get("name");
			List<Integer> trueList = new ArrayList<>();
			List<Integer> falseList = new ArrayList<>();
			trueList.add(trueQuad);
			falseList.add(falseQuad);
			gt.property.put("trueList", trueList);
			gt.property.put("falseList", trueList);
			BoolExpression trueExpression = new BoolExpression(condition);
			BoolExpression falseExpression = new BoolExpression();
			codes.add(trueExpression);
			codes.add(falseExpression);
		}break;
		case 40: { //B->E<=E
			int trueQuad = nextQuad();
			int falseQuad = nextQuad()+1;
			String condition = gt.children.get(2).property.get("name")+" <= "+gt.children.get(0).property.get("name");
			List<Integer> trueList = new ArrayList<>();
			List<Integer> falseList = new ArrayList<>();
			trueList.add(trueQuad);
			falseList.add(falseQuad);
			gt.property.put("trueList", trueList);
			gt.property.put("falseList", trueList);
			BoolExpression trueExpression = new BoolExpression(condition);
			BoolExpression falseExpression = new BoolExpression();
			codes.add(trueExpression);
			codes.add(falseExpression);
		}break;
		case 41: { //B->E>E
			int trueQuad = nextQuad();
			int falseQuad = nextQuad()+1;
			String condition = gt.children.get(2).property.get("name")+" > "+gt.children.get(0).property.get("name");
			List<Integer> trueList = new ArrayList<>();
			List<Integer> falseList = new ArrayList<>();
			trueList.add(trueQuad);
			falseList.add(falseQuad);
			gt.property.put("trueList", trueList);
			gt.property.put("falseList", trueList);
			BoolExpression trueExpression = new BoolExpression(condition);
			BoolExpression falseExpression = new BoolExpression();
			codes.add(trueExpression);
			codes.add(falseExpression);
		}break;
		case 42: { //B->E>=E
			int trueQuad = nextQuad();
			int falseQuad = nextQuad()+1;
			String condition = gt.children.get(2).property.get("name")+" >= "+gt.children.get(0).property.get("name");
			List<Integer> trueList = new ArrayList<>();
			List<Integer> falseList = new ArrayList<>();
			trueList.add(trueQuad);
			falseList.add(falseQuad);
			gt.property.put("trueList", trueList);
			gt.property.put("falseList", trueList);
			BoolExpression trueExpression = new BoolExpression(condition);
			BoolExpression falseExpression = new BoolExpression();
			codes.add(trueExpression);
			codes.add(falseExpression);
		}break;
		case 43: { //B->E==E
			int trueQuad = nextQuad();
			int falseQuad = nextQuad()+1;
			String condition = gt.children.get(2).property.get("name")+" == "+gt.children.get(0).property.get("name");
			List<Integer> trueList = new ArrayList<>();
			List<Integer> falseList = new ArrayList<>();
			trueList.add(trueQuad);
			falseList.add(falseQuad);
			gt.property.put("trueList", trueList);
			gt.property.put("falseList", trueList);
			BoolExpression trueExpression = new BoolExpression(condition);
			BoolExpression falseExpression = new BoolExpression();
			codes.add(trueExpression);
			codes.add(falseExpression);
		}break;
		case 44: { //B->E!=E
			int trueQuad = nextQuad();
			int falseQuad = nextQuad()+1;
			String condition = gt.children.get(2).property.get("name")+" != "+gt.children.get(0).property.get("name");
			List<Integer> trueList = new ArrayList<>();
			List<Integer> falseList = new ArrayList<>();
			trueList.add(trueQuad);
			falseList.add(falseQuad);
			gt.property.put("trueList", trueList);
			gt.property.put("falseList", trueList);
			BoolExpression trueExpression = new BoolExpression(condition);
			BoolExpression falseExpression = new BoolExpression();
			codes.add(trueExpression);
			codes.add(falseExpression);
		}break;
		case 45: { //B->true
			int trueQuad = nextQuad();
			List<Integer> trueList = new ArrayList<>();
			trueList.add(trueQuad);
			gt.property.put("trueList", trueList);
			BoolExpression trueExpression = new BoolExpression();
			codes.add(trueExpression);
		}break;
		case 46: { //B->false
			int flaseQuad = nextQuad();
			List<Integer> falseList = new ArrayList<>();
			falseList.add(flaseQuad);
			gt.property.put("falseList", falseList);
			BoolExpression falseExpression = new BoolExpression();
			codes.add(falseExpression);
		}break;
		case 47: { //M->nil
			gt.property.put("quad", nextQuad());
		}break;
		case 48: { //N->nil
			List<Integer> nextList = new ArrayList<>();
			nextList.add(nextQuad());
			gt.property.put("nextList", nextList);
		}break;
		case 49: //S->callid(Elist);
			break;
		case 50: //Elist->E,Elist
			break;
		case 51: //Elist->E
			break;
		case 52: //Elist->nil
			break;

		}
	}
	
	private static String newTemp() {
		String t_ = "t" + temp;
		while(curSymbolTable.lookUp(t_) != null) {
			temp++;
			t_ = "t" + temp;
		}
		temp++;
//		curSymbolTable.enter(t_, type, space, value);
		return t_;
	}
	
	private static int nextQuad() {
		return codes.size()+1;
	}
	
	private static void backPatch(List<Integer> list, int quad) {
		for(int i : list) {
			BoolExpression boolExpression = (BoolExpression)codes.get(i-1);
			boolExpression.setQuad(quad);
		}
	}
	
	public static String getCode() {
		StringBuilder sb = new StringBuilder();
		for(int i=1; i<=codes.size(); i++) {
			sb.append(i+": "+codes.get(i-1)+"\n");
		}
		return sb.toString();
	}
	
	public static void main(String[] args) {
//		File file = new File("Productions.txt");
//		try(FileReader fr = new FileReader(file);
//			BufferedReader br = new BufferedReader(fr)) {
//			String line = null;
//			int i = 1;
//			while((line = br.readLine()) != null) {
//				System.out.println("case " + (i++)+": //"+line.substring(5)+"\n\tbreak;");
//			}
//		} catch(IOException e) {
//			e.printStackTrace();
//		}
		
		StringBuilder a = new StringBuilder("a");
		StringBuilder b = new StringBuilder("b");
		List<Object> list = new ArrayList<>();
		list.add(a);
		list.add(b);
		StringBuilder c = (StringBuilder)list.get(1);
		c = a;
		System.out.println(b);
	}
}