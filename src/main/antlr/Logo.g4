grammar Logo;

options {
    caseInsensitive = true;
}

@header {
package dev.jb.logolsp;
}

program
    : separator* statement (separator+ statement)* separator* EOF
    ;

statement
    : procedureDefinition
    | procedureCall
    | variableAssignment
    | repeatStatement
    | ifElseStatement
    | ifStatement
    | turtleCommand
    | expressionStatement
    ;

procedureDefinition
    : TO IDENT parameter* separator* statementList END
    ;

parameter
    : variableReference
    ;

procedureCall
    : IDENT argument*
    ;

argument
    : expression
    | quotedWord
    ;

variableReference
    : COLON IDENT
    ;

variableAssignment
    : MAKE quotedWord expression
    ;

repeatStatement
    : REPEAT expression block
    ;

ifStatement
    : IF condition block
    ;

ifElseStatement
    : IFELSE condition block block
    ;

condition
    : expression comparator expression
    | expression
    ;

comparator
    : EQUALS
    | LT
    | GT
    ;

block
    : LBRACK separator* statementList? separator* RBRACK
    ;

statementList
    : statement (separator+ statement)*
    ;

expressionStatement
    : expression
    ;

expression
    : additiveExpression
    ;

additiveExpression
    : multiplicativeExpression ((PLUS | MINUS) multiplicativeExpression)*
    ;

multiplicativeExpression
    : unaryExpression ((STAR | SLASH) unaryExpression)*
    ;

unaryExpression
    : (PLUS | MINUS) unaryExpression
    | primaryExpression
    ;

primaryExpression
    : NUMBER
    | variableReference
    | quotedWord
    | LPAREN expression RPAREN
    ;

turtleCommand
    : forwardCommand
    | backCommand
    | rightCommand
    | leftCommand
    | penUpCommand
    | penDownCommand
    | homeCommand
    | clearScreenCommand
    ;

forwardCommand
    : FORWARD expression
    ;

backCommand
    : BACK expression
    ;

rightCommand
    : RIGHT expression
    ;

leftCommand
    : LEFT expression
    ;

penUpCommand
    : PENUP
    ;

penDownCommand
    : PENDOWN
    ;

homeCommand
    : HOME
    ;

clearScreenCommand
    : CLEARSCREEN
    ;

quotedWord
    : QUOTED_WORD
    ;

separator
    : NEWLINE+
    ;

TO: 'TO';
END: 'END';
MAKE: 'MAKE';
REPEAT: 'REPEAT';
IF: 'IF';
IFELSE: 'IFELSE';
FORWARD: 'FORWARD';
BACK: 'BACK';
RIGHT: 'RIGHT';
LEFT: 'LEFT';
PENUP: 'PENUP';
PENDOWN: 'PENDOWN';
HOME: 'HOME';
CLEARSCREEN: 'CLEARSCREEN';

EQUALS: '=';
LT: '<';
GT: '>';
PLUS: '+';
MINUS: '-';
STAR: '*';
SLASH: '/';
LPAREN: '(';
RPAREN: ')';
LBRACK: '[';
RBRACK: ']';
COLON: ':';

NUMBER
    : DIGIT+ ('.' DIGIT+)?
    | '.' DIGIT+
    ;

QUOTED_WORD
    : '"' LETTER (LETTER | DIGIT | '_')*
    ;

IDENT
    : LETTER (LETTER | DIGIT | '_')*
    ;

COMMENT
    : ';' ~[\r\n]* -> skip
    ;

NEWLINE
    : '\r'? '\n'
    ;

WS
    : [ \t\f]+ -> skip
    ;

fragment LETTER
    : [a-z_]
    ;

fragment DIGIT
    : [0-9]
    ;


