/*******************************************************************************
 * Copyright (c) 2000, 2018 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Stephan Herrmann - Contribution for Bug 463360 - [override method][null] generating method override should not create redundant null annotations
 *     Red Hat Inc. - removed some methods and put them in StubUtility2Core
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.codemanipulation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Dimension;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.TypeLocation;
import org.eclipse.jdt.core.manipulation.CodeGeneration;

import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.internal.corext.util.JDTUIHelperClasses;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.internal.ui.preferences.formatter.FormatterProfileManager;

/**
 * Utilities for code generation based on AST rewrite.
 *
 * @see StubUtility
 * @see JDTUIHelperClasses
 * @since 3.1
 */
public final class StubUtility2 {

	/* This method should work with all AST levels. */
	public static MethodDeclaration createConstructorStub(ICompilationUnit unit, ASTRewrite rewrite, ImportRewrite imports, ImportRewriteContext context, IMethodBinding binding, String type, int modifiers, boolean omitSuperForDefConst, boolean todo, CodeGenerationSettings settings) throws CoreException {
		AST ast= rewrite.getAST();
		MethodDeclaration decl= ast.newMethodDeclaration();
		decl.modifiers().addAll(ASTNodeFactory.newModifiers(ast, modifiers & ~Modifier.ABSTRACT & ~Modifier.NATIVE));
		decl.setName(ast.newSimpleName(type));
		decl.setConstructor(true);

		createTypeParameters(imports, context, ast, binding, decl);

		List<SingleVariableDeclaration> parameters= createParameters(unit.getJavaProject(), imports, context, ast, binding, null, decl);

		createThrownExceptions(decl, binding, imports, context, ast);

		Block body= ast.newBlock();
		decl.setBody(body);

		String delimiter= StubUtility.getLineDelimiterUsed(unit);
		String bodyStatement= ""; //$NON-NLS-1$
		if (!omitSuperForDefConst || !parameters.isEmpty()) {
			SuperConstructorInvocation invocation= ast.newSuperConstructorInvocation();
			SingleVariableDeclaration varDecl= null;
			for (Iterator<SingleVariableDeclaration> iterator= parameters.iterator(); iterator.hasNext();) {
				varDecl= iterator.next();
				invocation.arguments().add(ast.newSimpleName(varDecl.getName().getIdentifier()));
			}
			bodyStatement= ASTNodes.asFormattedString(invocation, 0, delimiter, FormatterProfileManager.getProjectSettings(unit.getJavaProject()));
		}

		if (todo) {
			String placeHolder= CodeGeneration.getMethodBodyContent(unit, type, binding.getName(), true, bodyStatement, delimiter);
			if (placeHolder != null) {
				ReturnStatement todoNode= (ReturnStatement) rewrite.createStringPlaceholder(placeHolder, ASTNode.RETURN_STATEMENT);
				body.statements().add(todoNode);
			}
		} else {
			ReturnStatement statementNode= (ReturnStatement) rewrite.createStringPlaceholder(bodyStatement, ASTNode.RETURN_STATEMENT);
			body.statements().add(statementNode);
		}

		if (settings != null && settings.createComments) {
			String string= CodeGeneration.getMethodComment(unit, type, decl, binding, delimiter);
			if (string != null) {
				Javadoc javadoc= (Javadoc) rewrite.createStringPlaceholder(string, ASTNode.JAVADOC);
				decl.setJavadoc(javadoc);
			}
		}
		return decl;
	}

	public static MethodDeclaration createConstructorStub(ICompilationUnit unit, ASTRewrite rewrite, ImportRewrite imports, ImportRewriteContext context, ITypeBinding typeBinding, IMethodBinding superConstructor, IVariableBinding[] variableBindings, int modifiers, CodeGenerationSettings settings) throws CoreException {
		AST ast= rewrite.getAST();

		MethodDeclaration decl= ast.newMethodDeclaration();
		decl.modifiers().addAll(ASTNodeFactory.newModifiers(ast, modifiers & ~Modifier.ABSTRACT & ~Modifier.NATIVE));
		decl.setName(ast.newSimpleName(typeBinding.getName()));
		decl.setConstructor(true);

		List<SingleVariableDeclaration> parameters= decl.parameters();
		if (superConstructor != null) {
			createTypeParameters(imports, context, ast, superConstructor, decl);

			createParameters(unit.getJavaProject(), imports, context, ast, superConstructor, null, decl);

			createThrownExceptions(decl, superConstructor, imports, context, ast);
		}

		Block body= ast.newBlock();
		decl.setBody(body);

		String delimiter= StubUtility.getLineDelimiterUsed(unit);

		if (superConstructor != null) {
			SuperConstructorInvocation invocation= ast.newSuperConstructorInvocation();
			SingleVariableDeclaration varDecl= null;
			for (Iterator<SingleVariableDeclaration> iterator= parameters.iterator(); iterator.hasNext();) {
				varDecl= iterator.next();
				invocation.arguments().add(ast.newSimpleName(varDecl.getName().getIdentifier()));
			}
			body.statements().add(invocation);
		}

		List<String> prohibited= new ArrayList<>();
		for (final Iterator<SingleVariableDeclaration> iterator= parameters.iterator(); iterator.hasNext();)
			prohibited.add(iterator.next().getName().getIdentifier());
		String param= null;
		List<String> list= new ArrayList<>(prohibited);
		String[] excluded= null;
		for (int i= 0; i < variableBindings.length; i++) {
			SingleVariableDeclaration var= ast.newSingleVariableDeclaration();
			var.setType(imports.addImport(variableBindings[i].getType(), ast, context, TypeLocation.PARAMETER));
			excluded= new String[list.size()];
			list.toArray(excluded);
			param= suggestParameterName(unit, variableBindings[i], excluded);
			list.add(param);
			var.setName(ast.newSimpleName(param));
			parameters.add(var);
		}

		list= new ArrayList<>(prohibited);
		for (int i= 0; i < variableBindings.length; i++) {
			excluded= new String[list.size()];
			list.toArray(excluded);
			final String paramName= suggestParameterName(unit, variableBindings[i], excluded);
			list.add(paramName);
			final String fieldName= variableBindings[i].getName();
			Expression expression= null;
			if (paramName.equals(fieldName) || settings.useKeywordThis) {
				FieldAccess access= ast.newFieldAccess();
				access.setExpression(ast.newThisExpression());
				access.setName(ast.newSimpleName(fieldName));
				expression= access;
			} else
				expression= ast.newSimpleName(fieldName);
			Assignment assignment= ast.newAssignment();
			assignment.setLeftHandSide(expression);
			assignment.setRightHandSide(ast.newSimpleName(paramName));
			assignment.setOperator(Assignment.Operator.ASSIGN);
			body.statements().add(ast.newExpressionStatement(assignment));
		}

		if (settings != null && settings.createComments) {
			String string= CodeGeneration.getMethodComment(unit, typeBinding.getName(), decl, superConstructor, delimiter);
			if (string != null) {
				Javadoc javadoc= (Javadoc) rewrite.createStringPlaceholder(string, ASTNode.JAVADOC);
				decl.setJavadoc(javadoc);
			}
		}
		return decl;
	}


	public static MethodDeclaration createImplementationStub(ICompilationUnit unit, ASTRewrite rewrite, ImportRewrite imports, ImportRewriteContext context,
			IMethodBinding binding, ITypeBinding targetType, CodeGenerationSettings settings, boolean inInterface, ASTNode astNode) throws CoreException {
		return createImplementationStub(unit, rewrite, imports, context, binding, null, targetType, settings, inInterface, astNode);
	}

	public static MethodDeclaration createImplementationStub(ICompilationUnit unit, ASTRewrite rewrite, ImportRewrite imports, ImportRewriteContext context,
			IMethodBinding binding, String[] parameterNames, ITypeBinding targetType, CodeGenerationSettings settings, boolean inInterface, ASTNode astNode) throws CoreException {
		Assert.isNotNull(imports);
		Assert.isNotNull(rewrite);

		AST ast= rewrite.getAST();
		String type= Bindings.getTypeQualifiedName(targetType);

		IJavaProject javaProject= unit.getJavaProject();
		EnumSet<TypeLocation> nullnessDefault= null;
		if (astNode != null && JavaCore.ENABLED.equals(javaProject.getOption(JavaCore.COMPILER_ANNOTATION_NULL_ANALYSIS, true)))
			nullnessDefault= RedundantNullnessTypeAnnotationsFilter.determineNonNullByDefaultLocations(astNode, RedundantNullnessTypeAnnotationsFilter.determineNonNullByDefaultNames(javaProject));

		MethodDeclaration decl= ast.newMethodDeclaration();
		decl.modifiers().addAll(getImplementationModifiers(ast, binding, inInterface, imports, context, nullnessDefault));

		decl.setName(ast.newSimpleName(binding.getName()));
		decl.setConstructor(false);
		
		ITypeBinding bindingReturnType= binding.getReturnType();
		bindingReturnType = StubUtility2Core.replaceWildcardsAndCaptures(bindingReturnType);
		
		if (JavaModelUtil.is50OrHigher(javaProject)) {
			createTypeParameters(imports, context, ast, binding, decl);
			
		} else {
			bindingReturnType= bindingReturnType.getErasure();
		}
		
		decl.setReturnType2(imports.addImport(bindingReturnType, ast, context, TypeLocation.RETURN_TYPE));

		List<SingleVariableDeclaration> parameters= createParameters(javaProject, imports, context, ast, binding, parameterNames, decl, nullnessDefault);

		createThrownExceptions(decl, binding, imports, context, ast);

		String delimiter= unit.findRecommendedLineSeparator();
		int modifiers= binding.getModifiers();
		ITypeBinding declaringType= binding.getDeclaringClass();
		ITypeBinding typeObject= ast.resolveWellKnownType("java.lang.Object"); //$NON-NLS-1$
		if (!inInterface || (declaringType != typeObject && JavaModelUtil.is18OrHigher(javaProject))) {
			// generate a method body

			Map<String, String> options= FormatterProfileManager.getProjectSettings(javaProject);

			Block body= ast.newBlock();
			decl.setBody(body);

			String bodyStatement= ""; //$NON-NLS-1$
			if (Modifier.isAbstract(modifiers)) {
				Expression expression= ASTNodeFactory.newDefaultExpression(ast, decl.getReturnType2(), decl.getExtraDimensions());
				if (expression != null) {
					ReturnStatement returnStatement= ast.newReturnStatement();
					returnStatement.setExpression(expression);
					bodyStatement= ASTNodes.asFormattedString(returnStatement, 0, delimiter, options);
				}
			} else {
				SuperMethodInvocation invocation= ast.newSuperMethodInvocation();
				if (declaringType.isInterface()) {
					ITypeBinding supertype= Bindings.findImmediateSuperTypeInHierarchy(targetType, declaringType.getTypeDeclaration().getQualifiedName());
					if (supertype == null) { // should not happen, but better use the type we have rather than failing
						supertype= declaringType;
					}
					if (supertype.isInterface()) {
						String qualifier= imports.addImport(supertype.getTypeDeclaration(), context);
						Name name= ASTNodeFactory.newName(ast, qualifier);
						invocation.setQualifier(name);
					}
				}
				invocation.setName(ast.newSimpleName(binding.getName()));
				SingleVariableDeclaration varDecl= null;
				for (Iterator<SingleVariableDeclaration> iterator= parameters.iterator(); iterator.hasNext();) {
					varDecl= iterator.next();
					invocation.arguments().add(ast.newSimpleName(varDecl.getName().getIdentifier()));
				}
				Expression expression= invocation;
				Type returnType= decl.getReturnType2();
				if (returnType instanceof PrimitiveType && ((PrimitiveType) returnType).getPrimitiveTypeCode().equals(PrimitiveType.VOID)) {
					bodyStatement= ASTNodes.asFormattedString(ast.newExpressionStatement(expression), 0, delimiter, options);
				} else {
					ReturnStatement returnStatement= ast.newReturnStatement();
					returnStatement.setExpression(expression);
					bodyStatement= ASTNodes.asFormattedString(returnStatement, 0, delimiter, options);
				}
			}

			String placeHolder= CodeGeneration.getMethodBodyContent(unit, type, binding.getName(), false, bodyStatement, delimiter);
			if (placeHolder != null) {
				ReturnStatement todoNode= (ReturnStatement) rewrite.createStringPlaceholder(placeHolder, ASTNode.RETURN_STATEMENT);
				body.statements().add(todoNode);
			}
		}

		if (settings != null && settings.createComments) {
			String string= CodeGeneration.getMethodComment(unit, type, decl, binding, delimiter);
			if (string != null) {
				Javadoc javadoc= (Javadoc) rewrite.createStringPlaceholder(string, ASTNode.JAVADOC);
				decl.setJavadoc(javadoc);
			}
		}

		// According to JLS8 9.2, an interface doesn't implicitly declare non-public members of Object,
		// and JLS8 9.6.4.4 doesn't allow @Override for these methods (clone and finalize).
		boolean skipOverride= inInterface && declaringType == typeObject && !Modifier.isPublic(modifiers);

		if (!skipOverride) {
			StubUtility2Core.addOverrideAnnotation(settings, javaProject, rewrite, imports, decl, binding.getDeclaringClass().isInterface(), null);			
		}

		return decl;
	}

	private static void createTypeParameters(ImportRewrite imports, ImportRewriteContext context, AST ast, IMethodBinding binding, MethodDeclaration decl) {
		ITypeBinding[] typeParams= binding.getTypeParameters();
		List<TypeParameter> typeParameters= decl.typeParameters();
		for (int i= 0; i < typeParams.length; i++) {
			ITypeBinding curr= typeParams[i];
			TypeParameter newTypeParam= ast.newTypeParameter();
			newTypeParam.setName(ast.newSimpleName(curr.getName()));
			ITypeBinding[] typeBounds= curr.getTypeBounds();
			if (typeBounds.length != 1 || !"java.lang.Object".equals(typeBounds[0].getQualifiedName())) {//$NON-NLS-1$
				List<Type> newTypeBounds= newTypeParam.typeBounds();
				for (int k= 0; k < typeBounds.length; k++) {
					newTypeBounds.add(imports.addImport(typeBounds[k], ast, context, TypeLocation.TYPE_BOUND));
				}
			}
			typeParameters.add(newTypeParam);
		}
	}

	private static List<SingleVariableDeclaration> createParameters(IJavaProject project, ImportRewrite imports, ImportRewriteContext context, AST ast, IMethodBinding binding, String[] paramNames, MethodDeclaration decl) {
		return createParameters(project, imports, context, ast, binding, paramNames, decl, null);
	}
	private static List<SingleVariableDeclaration> createParameters(IJavaProject project, ImportRewrite imports, ImportRewriteContext context, AST ast,
			IMethodBinding binding, String[] paramNames, MethodDeclaration decl, EnumSet<TypeLocation> nullnessDefault) {
		boolean is50OrHigher= JavaModelUtil.is50OrHigher(project);
		List<SingleVariableDeclaration> parameters= decl.parameters();
		ITypeBinding[] params= binding.getParameterTypes();
		if (paramNames == null || paramNames.length < params.length) {
			paramNames= StubUtility.suggestArgumentNames(project, binding);
		}
		for (int i= 0; i < params.length; i++) {
			SingleVariableDeclaration var= ast.newSingleVariableDeclaration();
			ITypeBinding type= params[i];
			type=StubUtility2Core.replaceWildcardsAndCaptures(type);
			if (!is50OrHigher) {
				type= type.getErasure();
				var.setType(imports.addImport(type, ast, context, TypeLocation.PARAMETER));
			} else if (binding.isVarargs() && type.isArray() && i == params.length - 1) {
				var.setVarargs(true);
				/*
				 * Varargs annotations are special.
				 * Example:
				 *     foo(@O Object @A [] @B ... arg)
				 * => @B is not an annotation on the array dimension that constitutes the vararg.
				 * It's the type annotation of the *innermost* array dimension.
				 */
				int dimensions= type.getDimensions();
				@SuppressWarnings("unchecked")
				List<Annotation>[] dimensionAnnotations= (List<Annotation>[]) new List<?>[dimensions];
				for (int dim= 0; dim < dimensions; dim++) {
					dimensionAnnotations[dim]= new ArrayList<>();
					for (IAnnotationBinding annotation : type.getTypeAnnotations()) {
						dimensionAnnotations[dim].add(imports.addAnnotation(annotation, ast, context));
					}
					type= type.getComponentType();
				}
				
				Type elementType= imports.addImport(type, ast, context);
				if (dimensions == 1) {
					var.setType(elementType);
				} else {
					ArrayType arrayType= ast.newArrayType(elementType, dimensions - 1);
					List<Dimension> dimensionNodes= arrayType.dimensions();
					for (int dim= 0; dim < dimensions - 1; dim++) { // all except the innermost dimension
						Dimension dimension= dimensionNodes.get(dim);
						dimension.annotations().addAll(dimensionAnnotations[dim]);
					}
					var.setType(arrayType);
				}
				List<Annotation> varargTypeAnnotations= dimensionAnnotations[dimensions - 1];
				var.varargsAnnotations().addAll(varargTypeAnnotations);
			} else {
				var.setType(imports.addImport(type, ast, context, TypeLocation.PARAMETER));
			}
			var.setName(ast.newSimpleName(paramNames[i]));
			IAnnotationBinding[] annotations= binding.getParameterAnnotations(i);
			for (IAnnotationBinding annotation : annotations) {
				if (StubUtility2Core.isCopyOnInheritAnnotation(annotation.getAnnotationType(), project, nullnessDefault, TypeLocation.PARAMETER))
					var.modifiers().add(imports.addAnnotation(annotation, ast, context));
			}
			parameters.add(var);
		}
		return parameters;
	}

	private static void createThrownExceptions(MethodDeclaration decl, IMethodBinding method, ImportRewrite imports, ImportRewriteContext context, AST ast) {
		ITypeBinding[] excTypes= method.getExceptionTypes();
		if (ast.apiLevel() >= AST.JLS8) {
			List<Type> thrownExceptions= decl.thrownExceptionTypes();
			for (int i= 0; i < excTypes.length; i++) {
				Type excType= imports.addImport(excTypes[i], ast, context, TypeLocation.EXCEPTION);
				thrownExceptions.add(excType);
			}
		} else {
			List<Name> thrownExceptions= getThrownExceptions(decl);
			for (int i= 0; i < excTypes.length; i++) {
				String excTypeName= imports.addImport(excTypes[i], context);
				thrownExceptions.add(ASTNodeFactory.newName(ast, excTypeName));
			}
		}
	}

	/**
	 * @param decl method declaration
	 * @return thrown exception names
	 * @deprecated to avoid deprecation warnings
	 */
	@Deprecated
	private static List<Name> getThrownExceptions(MethodDeclaration decl) {
		return decl.thrownExceptions();
	}

	private static List<IExtendedModifier> getImplementationModifiers(AST ast, IMethodBinding method, boolean inInterface, ImportRewrite importRewrite, ImportRewriteContext context, EnumSet<TypeLocation> nullnessDefault) throws JavaModelException {
		IJavaProject javaProject= importRewrite.getCompilationUnit().getJavaProject();
		int modifiers= method.getModifiers();
		if (inInterface) {
			modifiers= modifiers & ~Modifier.PROTECTED & ~Modifier.PUBLIC;
			if (Modifier.isAbstract(modifiers) && JavaModelUtil.is18OrHigher(javaProject)) {
				modifiers= modifiers | Modifier.DEFAULT;
			}
		} else {
			modifiers= modifiers & ~Modifier.DEFAULT;
		}
		modifiers= modifiers & ~Modifier.ABSTRACT & ~Modifier.NATIVE & ~Modifier.PRIVATE;
		IAnnotationBinding[] annotations= method.getAnnotations();
		
		if (modifiers != Modifier.NONE && annotations.length > 0) {
			// need an AST of the source method to preserve order of modifiers
			IMethod iMethod= (IMethod) method.getJavaElement();
			if (iMethod != null && JavaElementUtil.isSourceAvailable(iMethod)) {
				ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
				parser.setSource(iMethod.getTypeRoot());
				parser.setIgnoreMethodBodies(true);
				CompilationUnit otherCU= (CompilationUnit) parser.createAST(null);
				ASTNode otherMethod= NodeFinder.perform(otherCU, iMethod.getSourceRange());
				if (otherMethod instanceof MethodDeclaration) {
					MethodDeclaration otherMD= (MethodDeclaration) otherMethod;
					ArrayList<IExtendedModifier> result= new ArrayList<>();
					List<IExtendedModifier> otherModifiers= otherMD.modifiers();
					for (IExtendedModifier otherModifier : otherModifiers) {
						if (otherModifier instanceof Modifier) {
							int otherFlag= ((Modifier) otherModifier).getKeyword().toFlagValue();
							if ((otherFlag & modifiers) != 0) {
								modifiers= ~otherFlag & modifiers;
								result.addAll(ast.newModifiers(otherFlag));
							}
						} else {
							Annotation otherAnnotation= (Annotation) otherModifier;
							String n= otherAnnotation.getTypeName().getFullyQualifiedName();
							for (IAnnotationBinding annotation : annotations) {
								ITypeBinding otherAnnotationType= annotation.getAnnotationType();
								String qn= otherAnnotationType.getQualifiedName();
								if (qn.endsWith(n) && (qn.length() == n.length() || qn.charAt(qn.length() - n.length() - 1) == '.')) {
									if (StubUtility2Core.isCopyOnInheritAnnotation(otherAnnotationType, javaProject, nullnessDefault, TypeLocation.RETURN_TYPE))
										result.add(importRewrite.addAnnotation(annotation, ast, context));
									break;
								}
							}
						}
					}
					result.addAll(ASTNodeFactory.newModifiers(ast, modifiers));
					return result;
				}
			}
		}
		
		ArrayList<IExtendedModifier> result= new ArrayList<>();
		
		for (IAnnotationBinding annotation : annotations) {
			if (StubUtility2Core.isCopyOnInheritAnnotation(annotation.getAnnotationType(), javaProject, nullnessDefault, TypeLocation.RETURN_TYPE))
				result.add(importRewrite.addAnnotation(annotation, ast, context));
		}
		
		result.addAll(ASTNodeFactory.newModifiers(ast, modifiers));
		
		return result;
	}
	
	private static String suggestParameterName(ICompilationUnit unit, IVariableBinding binding, String[] excluded) {
		String name= StubUtility.getBaseName(binding, unit.getJavaProject());
		return StubUtility.suggestArgumentName(unit.getJavaProject(), name, excluded);
	}


	/**
	 * Creates a new stub utility.
	 */
	private StubUtility2() {
		// Not for instantiation
	}

}
