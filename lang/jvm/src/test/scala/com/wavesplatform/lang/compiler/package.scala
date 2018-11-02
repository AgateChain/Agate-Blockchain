package com.wavesplatform.lang

import cats.data.EitherT
import cats.kernel.Monoid
import com.wavesplatform.lang.Common.multiplierFunction
import com.wavesplatform.lang.ScriptVersion.Versions.V1
import com.wavesplatform.lang.v1.CTX
import com.wavesplatform.lang.v1.compiler.Terms.{CONST_LONG, EVALUATED}
import com.wavesplatform.lang.v1.compiler.Types._
import com.wavesplatform.lang.v1.evaluator.ctx.impl.PureContext
import com.wavesplatform.lang.v1.evaluator.ctx.{ARR, CaseType, LazyVal, NativeFunction}

package object compiler {

  val pointType   = CaseType("Point", List("x" -> LONG, "y" -> LONG))
  val listOfLongs = LIST
  val idT = NativeFunction("idT", 1, 10000: Short, TYPEPARAM('T'), "test id", ("p1", TYPEPARAM('T'), "p1")) {
    case a :: Nil => Right(a)
    case _        => ???
  }
  val returnsListLong =
    NativeFunction("undefinedOptionLong", 1, 1002: Short, LIST(LONG): TYPE, "test undefinedOptionLong")(_ => ???)
  val idOptionLong =
    NativeFunction("idOptionLong", 1, 1003: Short, UNIT, "test Some", ("opt", UNION(LONG, UNIT), "opt"))(_ => Right(PureContext.unit))
  val functionWithTwoPrarmsOfTheSameType =
    NativeFunction("functionWithTwoPrarmsOfTheSameType",
                   1,
                   1005: Short,
                   TYPEPARAM('T'),
                   "test same type params",
                   ("p1", TYPEPARAM('T'), "p1"),
                   ("p2", TYPEPARAM('T'), "p2"))(l => Right(l.head))

  private val arr = ARR(IndexedSeq[EVALUATED](null, null))
  val compilerContext = Monoid
    .combine(
      PureContext.build(V1),
      CTX(
        Seq(pointType, Common.pointTypeA, Common.pointTypeB),
        Map(
          ("p", ((Common.AorB, "Test variable"), null)),
          ("l", ((LIST(LONG), "Test list"), LazyVal(EitherT.pure(ARR(IndexedSeq(CONST_LONG(1L), CONST_LONG(2L))))))),
          ("lpa", ((LIST(Common.pointTypeA.typeRef), "Yet test list"), LazyVal(EitherT.pure(arr)))),
          ("lpabc", ((LIST(Common.AorBorC), "Yet another test list"), LazyVal(EitherT.pure(arr))))
        ),
        Array(multiplierFunction, functionWithTwoPrarmsOfTheSameType, idT, returnsListLong, idOptionLong)
      )
    )
    .compilerContext

}