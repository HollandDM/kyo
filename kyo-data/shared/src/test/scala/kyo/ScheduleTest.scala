package kyo

class ScheduleTest extends Test:

    "fixed" - {
        "returns correct next duration and same schedule" in {
            val interval             = 5.seconds
            val schedule             = Schedule.fixed(interval)
            val (next, nextSchedule) = schedule.next.get
            assert(next == interval)
            assert(nextSchedule == schedule)
        }

        "works with zero interval" in {
            val (next, _) = Schedule.fixed(Duration.Zero).next.get
            assert(next == Duration.Zero)
        }
    }

    "exponential" - {
        "increases interval exponentially" in {
            val schedule           = Schedule.exponential(1.second, 2.0)
            val (next1, schedule2) = schedule.next.get
            val (next2, _)         = schedule2.next.get
            assert(next1 == 1.second)
            assert(next2 == 2.seconds)
        }

        "works with factor less than 1" in {
            val schedule           = Schedule.exponential(1.second, 0.5)
            val (next1, schedule2) = schedule.next.get
            val (next2, _)         = schedule2.next.get
            assert(next1 == 1.second)
            assert(next2 == 500.millis)
        }

        "handles very large intervals" in {
            val schedule           = Schedule.exponential(365.days, 2.0)
            val (next1, schedule2) = schedule.next.get
            val (next2, _)         = schedule2.next.get
            assert(next1 == 365.days)
            assert(next2 == 730.days)
        }

        "works with factor of 1" in {
            val schedule           = Schedule.exponential(1.second, 1.0)
            val (next1, schedule2) = schedule.next.get
            val (next2, _)         = schedule2.next.get
            assert(next1 == 1.second)
            assert(next2 == 1.second)
        }
    }

    "fibonacci" - {
        "follows fibonacci sequence" in {
            val schedule           = Schedule.fibonacci(1.second, 1.second)
            val (next1, schedule2) = schedule.next.get
            val (next2, schedule3) = schedule2.next.get
            val (next3, _)         = schedule3.next.get
            assert(next1 == 1.second)
            assert(next2 == 1.second)
            assert(next3 == 2.seconds)
        }

        "works with different initial values" in {
            val schedule           = Schedule.fibonacci(1.second, 2.seconds)
            val (next1, schedule2) = schedule.next.get
            val (next2, schedule3) = schedule2.next.get
            val (next3, _)         = schedule3.next.get
            assert(next1 == 1.second)
            assert(next2 == 2.seconds)
            assert(next3 == 3.seconds)
        }

        "works with zero initial values" in {
            val schedule           = Schedule.fibonacci(Duration.Zero, Duration.Zero)
            val (next1, schedule2) = schedule.next.get
            val (next2, schedule3) = schedule2.next.get
            val (next3, _)         = schedule3.next.get
            assert(next1 == Duration.Zero)
            assert(next2 == Duration.Zero)
            assert(next3 == Duration.Zero)
        }
    }

    "immediate" - {
        "returns zero duration" in {
            val (next, nextSchedule) = Schedule.immediate.next.get
            assert(next == Duration.Zero)
            assert(nextSchedule == Schedule.done)
        }

        "always returns never as next schedule" in {
            assert(Schedule.immediate.next.flatMap(_._2.next).isEmpty)
        }
    }

    "never" - {
        "always returns infinite duration" in {
            assert(Schedule.never.next.isEmpty)
        }
    }

    "exponentialBackoff" - {
        "respects maxDelay" in {
            val initial            = 1.second
            val factor             = 2.0
            val maxDelay           = 4.seconds
            val schedule           = Schedule.exponentialBackoff(initial, factor, maxDelay)
            val (next1, schedule2) = schedule.next.get
            val (next2, schedule3) = schedule2.next.get
            val (next3, _)         = schedule3.next.get
            assert(next1 == 1.second)
            assert(next2 == 2.seconds)
            assert(next3 == 4.seconds)
        }

        "caps at maxDelay" in {
            val initial  = 1.second
            val factor   = 2.0
            val maxDelay = 4.seconds
            val schedule = Schedule.exponentialBackoff(initial, factor, maxDelay)
            var current  = schedule
            for _ <- 1 to 5 do
                val (nextDuration, nextSchedule) = current.next.get
                assert(nextDuration <= maxDelay)
                current = nextSchedule
            end for
            succeed
        }

        "works with factor less than 1" in {
            val initial            = 4.seconds
            val factor             = 0.5
            val maxDelay           = 4.seconds
            val schedule           = Schedule.exponentialBackoff(initial, factor, maxDelay)
            val (next1, schedule2) = schedule.next.get
            val (next2, _)         = schedule2.next.get
            assert(next1 == 4.seconds)
            assert(next2 == 2.seconds)
        }
    }

    "repeat" - {
        "repeats specified number of times" in {
            val schedule           = Schedule.repeat(3)
            val (next1, schedule2) = schedule.next.get
            val (next2, schedule3) = schedule2.next.get
            val (next3, schedule4) = schedule3.next.get
            val next4              = schedule4.next
            assert(next1 == Duration.Zero)
            assert(next2 == Duration.Zero)
            assert(next3 == Duration.Zero)
            assert(next4.isEmpty)
        }

        "works with zero repetitions" in {
            val schedule = Schedule.repeat(0)
            assert(schedule.next.isEmpty)
        }

        "works with finite inner schedule" in {
            val innerSchedule = Schedule.fixed(1.second).take(2)
            val s             = innerSchedule.repeat(3)
            val results = List.unfold(s) { schedule =>
                schedule.next.map((next, newSchedule) => Some((next, newSchedule))).getOrElse(None)
            }
            assert(results == List(1.second, 1.second, 1.second, 1.second, 1.second, 1.second))
        }

        "repeats correct number of times with complex inner schedule" in {
            val s           = Schedule.immediate.andThen(Schedule.fixed(1.second).take(1)).repeat(2)
            val (next1, s2) = s.next.get
            val (next2, s3) = s2.next.get
            val (next3, s4) = s3.next.get
            val (next4, s5) = s4.next.get
            val next5       = s5.next
            assert(next1 == Duration.Zero)
            assert(next2 == 1.second)
            assert(next3 == Duration.Zero)
            assert(next4 == 1.second)
            assert(next5.isEmpty)
        }

        "Schedule.repeat" - {
            "repeats immediate schedule specified number of times" in {
                val s           = Schedule.repeat(3)
                val (next1, s2) = s.next.get
                val (next2, s3) = s2.next.get
                val (next3, s4) = s3.next.get
                val next4       = s4.next

                assert(next1 == Duration.Zero)
                assert(next2 == Duration.Zero)
                assert(next3 == Duration.Zero)
                assert(next4.isEmpty)
            }

            "works with zero repetitions" in {
                assert(Schedule.repeat(0).next.isEmpty)
            }

            "can be chained with other schedules" in {
                val s           = Schedule.repeat(2).andThen(Schedule.fixed(1.second))
                val (next1, s2) = s.next.get
                val (next2, s3) = s2.next.get
                val (next3, s4) = s3.next.get
                val (next4, _)  = s4.next.get

                assert(next1 == Duration.Zero)
                assert(next2 == Duration.Zero)
                assert(next3 == 1.second)
                assert(next4 == 1.second)
            }

        }
    }

    "linear" - {
        "increases interval linearly" in {
            val base               = 1.second
            val schedule           = Schedule.linear(base)
            val (next1, schedule2) = schedule.next.get
            val (next2, schedule3) = schedule2.next.get
            val (next3, _)         = schedule3.next.get
            assert(next1 == 1.second)
            assert(next2 == 2.seconds)
            assert(next3 == 4.seconds)
        }

        "works with zero base" in {
            val schedule           = Schedule.linear(Duration.Zero)
            val (next1, schedule2) = schedule.next.get
            val (next2, _)         = schedule2.next.get
            assert(next1 == Duration.Zero)
            assert(next2 == Duration.Zero)
        }
    }

    "max" - {
        "returns later of two schedules" in {
            val s1        = Schedule.fixed(1.second)
            val s2        = Schedule.fixed(2.seconds)
            val combined  = s1.max(s2)
            val (next, _) = combined.next.get
            assert(next == 2.seconds)
        }

        "handles one schedule being never" in {
            val s1 = Schedule.fixed(1.second)
            val s2 = Schedule.never
            assert(s1.max(s2).next.isEmpty)
        }

        "handles both schedules being never" in {
            val combined = Schedule.never.max(Schedule.never)
            assert(combined.next.isEmpty)
        }
    }

    "min" - {
        "returns earlier of two schedules" in {
            val s1        = Schedule.fixed(1.second)
            val s2        = Schedule.fixed(2.seconds)
            val combined  = s1.min(s2)
            val (next, _) = combined.next.get
            assert(next == 1.second)
        }

        "handles one schedule being immediate" in {
            val s1        = Schedule.fixed(1.second)
            val s2        = Schedule.immediate
            val combined  = s1.min(s2)
            val (next, _) = combined.next.get
            assert(next == Duration.Zero)
        }

        "handles both schedules being immediate" in {
            val combined  = Schedule.immediate.min(Schedule.immediate)
            val (next, _) = combined.next.get
            assert(next == Duration.Zero)
        }
    }

    "take" - {
        "limits number of executions" in {
            val s           = Schedule.fixed(1.second).take(2)
            val (next1, s2) = s.next.get
            val (next2, s3) = s2.next.get
            val next3       = s3.next
            assert(next1 == 1.second)
            assert(next2 == 1.second)
            assert(next3.isEmpty)
        }

        "returns never for non-positive count" in {
            val s = Schedule.fixed(1.second).take(0)
            assert(s == Schedule.done)
        }
    }

    "andThen" - {
        "switches to second schedule after first completes" in {
            val s1          = Schedule.repeat(2)
            val s2          = Schedule.fixed(1.second)
            val combined    = s1.andThen(s2)
            val (next1, c2) = combined.next.get
            val (next2, c3) = c2.next.get
            val (next3, _)  = c3.next.get
            assert(next1 == Duration.Zero)
            assert(next2 == Duration.Zero)
            assert(next3 == 1.second)
        }

        "works with never as first schedule" in {
            val s1       = Schedule.never
            val s2       = Schedule.fixed(1.second)
            val combined = s1.andThen(s2)
            assert(combined.next.isEmpty)
        }

        "works with never as second schedule" in {
            val s1          = Schedule.immediate
            val s2          = Schedule.never
            val combined    = s1.andThen(s2)
            val (next1, c2) = combined.next.get
            val next2       = c2.next
            assert(next1 == Duration.Zero)
            assert(next2.isEmpty)
        }

        "chains multiple schedules" in {
            val s           = Schedule.immediate.andThen(Schedule.fixed(1.second).take(1)).andThen(Schedule.fixed(2.seconds).take(1))
            val (next1, s2) = s.next.get
            val (next2, s3) = s2.next.get
            val (next3, s4) = s3.next.get
            val next4       = s4.next
            assert(next1 == Duration.Zero)
            assert(next2 == 1.second)
            assert(next3 == 2.seconds)
            assert(next4.isEmpty)
        }
    }

    "maxDuration" - {
        "stops after specified duration" in {
            val s           = Schedule.fixed(1.second).maxDuration(2.seconds + 500.millis)
            val (next1, s2) = s.next.get
            val (next2, s3) = s2.next.get
            val next3       = s3.next
            assert(next1 == 1.second)
            assert(next2 == 1.second)
            assert(next3.isEmpty)
        }

        "works with zero duration" in {
            val s = Schedule.fixed(1.second).maxDuration(Duration.Zero)
            assert(s.next.isEmpty)
        }

        "works with complex schedule" in {
            val s = Schedule.exponential(1.second, 2.0).repeat(5).maxDuration(7.seconds)
            val results = List.unfold(s) { schedule =>
                schedule.next.map((next, newSchedule) => Some((next, newSchedule))).getOrElse(None)
            }
            assert(results == List(1.second, 2.seconds, 4.seconds))
        }

        "limits duration correctly with delayed start" in {
            val s           = Schedule.fixed(2.seconds).take(1).andThen(Schedule.linear(1.second)).maxDuration(5.seconds)
            val (next1, s2) = s.next.get
            val (next2, s3) = s2.next.get
            val (next3, s4) = s3.next.get
            val next4       = s4.next
            assert(next1 == 2.seconds)
            assert(next2 == 1.second)
            assert(next3 == 2.seconds)
            assert(next4.isEmpty)
        }
    }

    "forever" - {
        "repeats indefinitely" in {
            val s           = Schedule.repeat(1).forever
            val (next1, s2) = s.next.get
            val (next2, s3) = s2.next.get
            val (next3, _)  = s3.next.get
            assert(next1 == Duration.Zero)
            assert(next2 == Duration.Zero)
            assert(next3 == Duration.Zero)
        }

        "works with never schedule" in {
            assert(Schedule.never.forever.next.isEmpty)
        }

        "works with immediate schedule" in {
            val s           = Schedule.immediate.forever
            val (next1, s2) = s.next.get
            val (next2, _)  = s2.next.get
            assert(next1 == Duration.Zero)
            assert(next2 == Duration.Zero)
        }

        "works with fixed schedule" in {
            val s           = Schedule.fixed(1.second).forever
            val (next1, s2) = s.next.get
            val (next2, s3) = s2.next.get
            val (next3, _)  = s3.next.get
            assert(next1 == 1.second)
            assert(next2 == 1.second)
            assert(next3 == 1.second)
        }

        "works with exponential schedule" in {
            val s           = Schedule.exponential(1.second, 2.0).forever
            val (next1, s2) = s.next.get
            val (next2, s3) = s2.next.get
            val (next3, _)  = s3.next.get
            assert(next1 == 1.second)
            assert(next2 == 2.seconds)
            assert(next3 == 4.seconds)
        }

        "works with complex schedule" in {
            val s           = (Schedule.immediate.andThen(Schedule.fixed(1.second).take(1))).forever
            val (next1, s2) = s.next.get
            val (next2, s3) = s2.next.get
            val (next3, s4) = s3.next.get
            val (next4, _)  = s4.next.get
            assert(next1 == Duration.Zero)
            assert(next2 == 1.second)
            assert(next3 == Duration.Zero)
            assert(next4 == 1.second)
        }
    }

    "delay" - {
        "adds fixed delay to each interval" in {
            val original = Schedule.fixed(1.second)
            val delayed  = original.delay(500.millis)

            val (next1, s2) = delayed.next.get
            val (next2, _)  = s2.next.get

            assert(next1 == 1500.millis)
            assert(next2 == 1500.millis)
        }

        "works with zero delay" in {
            val original = Schedule.fixed(1.second)
            val delayed  = original.delay(Duration.Zero)

            val (next, _) = delayed.next.get

            assert(next == 1.second)
        }

        "works with immediate schedule" in {
            val delayed = Schedule.immediate.delay(1.second)

            val (next1, s1) = delayed.next.get
            val next2       = s1.next

            assert(next1 == 1.second)
            assert(next2.isEmpty)
        }

        "works with never schedule" in {
            val delayed = Schedule.never.delay(1.second)
            assert(delayed.next.isEmpty)
        }

        "works with complex schedule" in {
            val original = Schedule.exponential(1.second, 2.0).take(3)
            val delayed  = original.delay(500.millis)

            val (next1, s2) = delayed.next.get
            val (next2, s3) = s2.next.get
            val (next3, s4) = s3.next.get
            val next4       = s4.next

            assert(next1 == 1500.millis)
            assert(next2 == 2500.millis)
            assert(next3 == 4500.millis)
            assert(next4.isEmpty)
        }

        "Schedule.delay" - {
            "creates a delayed immediate schedule" in {
                val s           = Schedule.delay(1.second)
                val (next1, s2) = s.next.get
                val next2       = s2.next

                assert(next1 == 1.second)
                assert(next2.isEmpty)
            }

            "works with zero delay" in {
                val s         = Schedule.delay(Duration.Zero)
                val (next, _) = s.next.get

                assert(next == Duration.Zero)
            }

            "can be chained with other schedules" in {
                val s           = Schedule.delay(500.millis).andThen(Schedule.fixed(1.second))
                val (next1, s2) = s.next.get
                val (next2, _)  = s2.next.get

                assert(next1 == 500.millis)
                assert(next2 == 1.second)
            }

            "works in combination with other schedules" in {
                val s1       = Schedule.fixed(1.second).take(2)
                val s2       = Schedule.exponential(2.seconds, 2.0).take(2)
                val combined = s1.andThen(Schedule.delay(3.seconds)).andThen(s2)

                val (next1, c2) = combined.next.get
                val (next2, c3) = c2.next.get
                val (next3, c4) = c3.next.get
                val (next4, c5) = c4.next.get
                val (next5, _)  = c5.next.get

                assert(next1 == 1.second)
                assert(next2 == 1.second)
                assert(next3 == 3.seconds)
                assert(next4 == 2.seconds)
                assert(next5 == 4.seconds)
            }
        }
    }

    "complex schedules" - {
        "combines max and min schedules" in {
            val s1       = Schedule.fixed(1.second)
            val s2       = Schedule.fixed(2.seconds)
            val s3       = Schedule.fixed(3.seconds)
            val combined = s1.max(s2).min(s3)

            val (next1, c2) = combined.next.get
            val (next2, _)  = c2.next.get

            assert(next1 == 2.seconds)
            assert(next2 == 2.seconds)
        }

        "limits a forever schedule" in {
            val s = Schedule.exponential(1.second, 2.0).forever.maxDuration(5.seconds)

            val (next1, s2) = s.next.get
            val (next2, s3) = s2.next.get
            val next3       = s3.next

            assert(next1 == 1.second)
            assert(next2 == 2.seconds)
            assert(next3.isEmpty)
        }

        "combines repeat with exponential backoff" in {
            val s = Schedule.repeat(3).andThen(Schedule.exponentialBackoff(1.second, 2.0, 8.seconds))

            val (next1, s2) = s.next.get
            val (next2, s3) = s2.next.get
            val (next3, s4) = s3.next.get
            val (next4, s5) = s4.next.get
            val (next5, _)  = s5.next.get

            assert(next1 == Duration.Zero)
            assert(next2 == Duration.Zero)
            assert(next3 == Duration.Zero)
            assert(next4 == 1.second)
            assert(next5 == 2.seconds)
        }
    }

    "schedule reduction and equality" - {
        "max reduction" in {
            val s1 = Schedule.fixed(1.second)
            val s2 = Schedule.fixed(2.seconds)
            val s3 = Schedule.never
            val s4 = Schedule.immediate

            assert(s1.max(s3) == s3)
            assert(s3.max(s1) == s3)
            assert(s1.max(s4) == s1)
            assert(s4.max(s1) == s1)
            assert(s3.max(s4) == s3)
            assert(s4.max(s3) == s3)
        }

        "min reduction" in {
            val s1 = Schedule.fixed(1.second)
            val s2 = Schedule.fixed(2.seconds)
            val s3 = Schedule.never
            val s4 = Schedule.immediate

            assert(s1.min(s3) == s1)
            assert(s3.min(s1) == s1)
            assert(s1.min(s4) == s4)
            assert(s4.min(s1) == s4)
            assert(s3.min(s4) == s4)
            assert(s4.min(s3) == s4)
        }

        "take reduction" in {
            val s1 = Schedule.fixed(1.second)

            assert(s1.take(0) == Schedule.done)
            assert(Schedule.never.take(3) == Schedule.never)
        }

        "andThen reduction" in {
            val s1 = Schedule.fixed(1.second)
            val s2 = Schedule.fixed(2.seconds)

            assert(Schedule.never.andThen(s1) == Schedule.never)
        }

        "maxDuration reduction" in {
            val s1       = Schedule.fixed(1.second)
            val duration = 5.seconds

            assert(Schedule.never.maxDuration(duration) == Schedule.never)
        }

        "forever reduction" in {
            val s1 = Schedule.fixed(1.second)

            assert(Schedule.never.forever == Schedule.never)
            assert(Schedule.done.forever == Schedule.done)
        }

        "correctly compares complex schedules" in {
            val s1 = Schedule.exponential(1.second, 2.0).take(3)
            val s2 = Schedule.exponential(1.second, 2.0).take(3)
            val s3 = Schedule.exponential(1.second, 2.0).take(4)

            assert(s1 == s2)
            assert(s1 != s3)
        }

        "handles equality with forever schedules" in {
            val s1 = Schedule.fixed(1.second).forever
            val s2 = Schedule.fixed(1.second).forever
            val s3 = Schedule.fixed(2.seconds).forever

            assert(s1 == s2)
            assert(s1 != s3)
        }

        "reduces exponential schedule with factor 1 to fixed schedule" in {
            val s1 = Schedule.exponential(1.second, 1.0)
            val s2 = Schedule.fixed(1.second)

            assert(s1 == s2)
        }

        "reduces exponential backoff schedule with factor 1 to fixed schedule" in {
            val s1 = Schedule.exponentialBackoff(1.second, 1.0, 10.seconds)
            val s2 = Schedule.fixed(1.second)

            assert(s1 == s2)
        }

        "reduces linear schedule with zero base to immediate schedule" in {
            val s1 = Schedule.linear(Duration.Zero)
            val s2 = Schedule.immediate.forever

            assert(s1 == s2)
        }

        "reduces fibonacci schedule with zero initial values to immediate forever" in {
            val s1 = Schedule.fibonacci(Duration.Zero, Duration.Zero)
            val s2 = Schedule.immediate.forever

            assert(s1 == s2)
        }

        "reduces exponential schedule with zero initial to immediate" in {
            val s1 = Schedule.exponential(Duration.Zero, 2.0)
            val s2 = Schedule.immediate

            assert(s1 == s2)
        }

        "reduces delay with zero duration to original schedule" in {
            val original = Schedule.fixed(1.second)
            val delayed  = original.delay(Duration.Zero)
            assert(delayed == original)
        }

        "reduces maxDuration with infinite duration to original schedule" in {
            val original = Schedule.fixed(1.second)
            val limited  = original.maxDuration(Duration.Infinity)
            assert(limited == original)
        }

        "reduces repeat with count 1 to original schedule" in {
            val original = Schedule.fixed(1.second)
            val repeated = original.repeat(1)
            assert(repeated == original)
        }

        "reduces andThen with immediate to original schedule" in {
            val original = Schedule.fixed(1.second)
            val chained  = original.andThen(Schedule.immediate)
            assert(chained == original)
        }

        "reduces forever of forever to single forever" in {
            val original      = Schedule.fixed(1.second)
            val doubleForever = original.forever
            assert(doubleForever == original.forever)
        }

        "reduces delay of never to never" in {
            val delayed = Schedule.never.delay(1.second)
            assert(delayed == Schedule.never)
        }

        "reduces maxDuration of immediate to immediate" in {
            val limited = Schedule.immediate.maxDuration(1.second)
            assert(limited == Schedule.immediate)
        }

        "reduces andThen of done and any schedule to that schedule" in {
            val s       = Schedule.fixed(1.second)
            val chained = Schedule.done.andThen(s)
            assert(chained == s)
        }

        "reduces repeat with count 0 to done" in {
            val original = Schedule.fixed(1.second)
            val repeated = original.repeat(0)
            assert(repeated == Schedule.done)
        }

        "reduces take with count 0 to done" in {
            val original = Schedule.fixed(1.second)
            val taken    = original.take(0)
            assert(taken == Schedule.done)
        }

        "reduces andThen with never to original schedule" in {
            val original = Schedule.fixed(1.second)
            val chained  = original.andThen(Schedule.never)
            assert(chained == original)
        }

        "reduces max of done" in {
            val s     = Schedule.fixed(1.second)
            val maxed = Schedule.done.max(s)
            assert(maxed == s)
        }

        "reduces min of never and any schedule to that schedule" in {
            val s      = Schedule.fixed(1.second)
            val minned = Schedule.never.min(s)
            assert(minned == s)
        }

        "reduces delay of done to done" in {
            val delayed = Schedule.done.delay(1.second)
            assert(delayed == Schedule.done)
        }

        "reduces delay of immediate to fixed delay" in {
            val delayed = Schedule.immediate.delay(1.second)
            assert(delayed == Schedule.delay(1.second))
        }

        "reduces maxDuration of never to never" in {
            val limited = Schedule.never.maxDuration(1.second)
            assert(limited == Schedule.never)
        }

        "reduces forever of never to never" in {
            val foreverNever = Schedule.never.forever
            assert(foreverNever == Schedule.never)
        }

        "reduces forever of done to done" in {
            val foreverDone = Schedule.done.forever
            assert(foreverDone == Schedule.done)
        }
    }

    "show" - {
        "correctly represents simple schedules" in {
            assert(Schedule.immediate.show == "Schedule.immediate")
            assert(Schedule.never.show == "Schedule.never")
            assert(Schedule.done.show == "Schedule.done")
            assert(Schedule.fixed(1.second).show == s"Schedule.fixed(${1.second.show})")
            assert(Schedule.linear(2.seconds).show == s"Schedule.linear(${2.seconds.show})")
            assert(Schedule.exponential(1.second, 2.0).show == s"Schedule.exponential(${1.second.show}, 2.0)")
            assert(Schedule.fibonacci(1.second, 2.seconds).show == s"Schedule.fibonacci(${1.second.show}, ${2.seconds.show})")
            assert(Schedule.exponentialBackoff(1.second, 2.0, 10.seconds)
                .show == s"Schedule.exponentialBackoff(${1.second.show}, 2.0, ${10.seconds.show})")
        }

        "correctly represents composite schedules" in {
            val s1 = Schedule.fixed(1.second).take(3)
            assert(s1.show == s"(Schedule.fixed(${1.second.show})).take(3)")

            val s2 = Schedule.exponential(1.second, 2.0).forever
            assert(s2.show == s"(Schedule.exponential(${1.second.show}, 2.0)).forever")

            val s3 = Schedule.fixed(1.second).max(Schedule.fixed(2.seconds))
            assert(s3.show == s"(Schedule.fixed(${1.second.show})).max(Schedule.fixed(${2.seconds.show}))")

            val s4 = Schedule.immediate.andThen(Schedule.fixed(1.second))
            assert(s4.show == s"(Schedule.immediate).andThen(Schedule.fixed(${1.second.show}))")
        }

        "correctly represents complex composite schedules" in {
            val s = Schedule.exponential(1.second, 2.0)
                .take(5)
                .andThen(Schedule.fixed(10.seconds))
                .forever
                .maxDuration(1.minute)

            assert(
                s.show == s"((((Schedule.exponential(${1.second.show}, 2.0)).take(5)).andThen(Schedule.fixed(${10.seconds.show}))).forever).maxDuration(${1.minute.show})"
            )
        }

        "correctly represents schedules with delay" in {
            val s1 = Schedule.fixed(1.second).delay(500.millis)
            assert(s1.show == s"(Schedule.fixed(${1.second.show})).delay(${500.millis.show})")
        }
    }

end ScheduleTest