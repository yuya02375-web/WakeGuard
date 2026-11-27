(() => {
  "use strict";

  const defaults = Object.freeze({
    equipment: "body",
    level: "beginner",
    goal: "habit",
    days: 3,
    minutes: 20,
  });

  const labels = Object.freeze({
    equipment: { body: "自重のみ", dumbbell: "ダンベルあり", gym: "ジム" },
    level: { beginner: "初心者", regular: "経験あり", advanced: "上級者" },
    goal: { strength: "筋力・筋肉をつける", habit: "運動を習慣にする", endurance: "持久力をつける" },
    readiness: { light: "軽め", normal: "いつも通り", strong: "調子がいい" },
  });

  function exercise(id, name, group, equipment, unit, base, description, cues, load = false) {
    return Object.freeze({ id, name, group, equipment, unit, base, description, cues: Object.freeze(cues), load });
  }

  const catalog = Object.freeze({
    pushup: exercise("pushup", "腕立て伏せ", "胸・腕", "body", "reps", 6,
      "胸と腕で床を押す自重トレーニングです。",
      ["頭からかかとまでを一直線にする。", "肘を真横に開きすぎず、胸をゆっくり下ろす。"]),
    kneePushup: exercise("kneePushup", "膝つき腕立て伏せ", "胸・腕", "body", "reps", 8,
      "膝をついて腕立て伏せの動きを練習します。",
      ["肩から膝までを一直線に保つ。", "胸を床に近づけ、手のひら全体で押し戻す。"]),
    squat: exercise("squat", "スクワット", "脚・お尻", "body", "reps", 10,
      "太ももとお尻を使って、しゃがむ動きを練習します。",
      ["足裏全体で床を踏み、膝をつま先と同じ方向に向ける。", "動きをコントロールできる深さから始める。"]),
    crunch: exercise("crunch", "クランチ", "体幹", "body", "reps", 8,
      "肩甲骨を少し持ち上げて、お腹に力を入れます。",
      ["膝を曲げて仰向けになる。", "息を吐きながら、お腹を縮めて肩を持ち上げる。"]),
    bridge: exercise("bridge", "ヒップリフト", "お尻・もも裏", "body", "reps", 10,
      "仰向けの姿勢からお尻を持ち上げます。",
      ["両足を床につけ、膝を曲げる。", "腰を反らせすぎず、お尻に力を入れて持ち上げる。"]),
    birdDog: exercise("birdDog", "バードドッグ", "体幹・背中", "body", "reps", 8,
      "四つんばいで、体幹の安定を練習します。左右交互に行い、両側の合計を記録します。",
      ["手は肩の下、膝は股関節の下に置く。", "腰をひねらず、対角の手と脚を伸ばす。"]),
    proneY: exercise("proneY", "うつ伏せYレイズ", "肩・背中", "body", "reps", 8,
      "うつ伏せで両腕を斜め前に上げ、肩甲骨を動かします。",
      ["両腕でY字を作り、親指を上に向ける。", "首を伸ばしたまま両手を少し浮かせ、ゆっくり戻す。"]),
    reverseLunge: exercise("reverseLunge", "リバースランジ", "脚・お尻", "body", "reps", 8,
      "片足ずつ後ろへ踏み出します。左右交互に行い、両側の合計を記録します。",
      ["上体を起こし、前足の足裏全体で床を踏む。", "前足の膝をつま先と同じ方向に向ける。"]),
    plank: exercise("plank", "プランク", "体幹", "body", "sec", 15,
      "前腕とつま先で体を支えます。",
      ["肘を肩の下に置く。", "呼吸を続け、腰を反らさず姿勢を保つ。"]),
    dumbbellSquat: exercise("dumbbellSquat", "ゴブレットスクワット", "脚・お尻", "dumbbell", "reps", 8,
      "ダンベルを胸の前に抱えてしゃがみます。ダンベル1個分の重さを記録します。",
      ["ダンベルを体の近くに保つ。", "足裏全体を床につけたまま、ゆっくりしゃがむ。"], true),
    dumbbellRow: exercise("dumbbellRow", "ワンハンドダンベルロー", "背中・腕", "dumbbell", "reps", 8,
      "片手で重りを引きます。左右同じ回数を行い、片側の回数と重さを記録します。",
      ["空いている手を安定した台か太ももに置く。", "上体の向きを保ち、肘を腰に向けて引く。"], true),
    floorPress: exercise("floorPress", "ダンベルフロアプレス", "胸・腕", "dumbbell", "reps", 8,
      "床に仰向けになって重りを押します。ダンベル片手分の重さを記録します。",
      ["膝を立て、重りの真下に肘を置く。", "上腕を床にそっとつけてから押し戻す。"], true),
    dumbbellHinge: exercise("dumbbellHinge", "ダンベルルーマニアンデッドリフト", "お尻・もも裏", "dumbbell", "reps", 8,
      "股関節を曲げ伸ばしします。ダンベル片手分の重さを記録します。",
      ["膝を軽く曲げたまま、お尻を後ろへ引く。", "背中の姿勢を保ち、重りを脚の近くで動かす。"], true),
    legPress: exercise("legPress", "レッグプレス", "脚・お尻", "gym", "reps", 8,
      "マシンで脚を伸ばし、プレートを押します。",
      ["背中と骨盤をシートにつける。", "膝とつま先を同じ方向に向けて、ゆっくり戻す。"], true),
    latPulldown: exercise("latPulldown", "ラットプルダウン", "背中・腕", "gym", "reps", 8,
      "マシンのバーを胸の上部に引きます。",
      ["足を床につけ、バーを手のひらで握る。", "体を大きく揺らさず、肘を下げて引く。"], true),
    chestPress: exercise("chestPress", "マシンチェストプレス", "胸・腕", "gym", "reps", 8,
      "座った姿勢でハンドルを前に押します。",
      ["グリップが胸の高さになるように調整する。", "肩をすくめず、ゆっくり押してゆっくり戻す。"], true),
    legCurl: exercise("legCurl", "レッグカール", "もも裏", "gym", "reps", 8,
      "マシンに合わせた姿勢で膝を曲げ、もも裏を使います。",
      ["膝をマシンの回転軸に合わせる。", "胴体を安定させ、戻す動作もゆっくり行う。"], true),
  });

  const schedules = {
    body: [
      ["squat", "pushup", "crunch", "proneY", "bridge"],
      ["reverseLunge", "kneePushup", "birdDog", "proneY", "bridge"],
      ["bridge", "pushup", "plank", "proneY", "squat"],
    ],
    dumbbell: [
      ["dumbbellSquat", "floorPress", "dumbbellRow", "plank", "dumbbellHinge"],
      ["dumbbellHinge", "dumbbellRow", "floorPress", "birdDog", "reverseLunge"],
      ["dumbbellSquat", "dumbbellRow", "floorPress", "crunch", "bridge"],
    ],
    gym: [
      ["legPress", "chestPress", "latPulldown", "crunch", "legCurl"],
      ["legCurl", "latPulldown", "chestPress", "birdDog", "legPress"],
      ["legPress", "latPulldown", "chestPress", "plank", "legCurl"],
    ],
  };

  function oneOf(values, value, fallback) {
    return Object.hasOwn(values, value) ? value : fallback;
  }

  function profile(values = {}) {
    values = values || {};
    const goal = oneOf(labels.goal, values.goal, defaults.goal);
    const recommendedDays = { strength: 3, habit: 3, endurance: 4 };
    return {
      equipment: oneOf(labels.equipment, values.equipment, defaults.equipment),
      level: oneOf(labels.level, values.level, defaults.level),
      goal,
      days: [2, 3, 4, 5].includes(Number(values.days)) ? Number(values.days) : recommendedDays[goal],
      minutes: [10, 20, 30, 45].includes(Number(values.minutes)) ? Number(values.minutes) : defaults.minutes,
    };
  }

  function available(values) {
    const { equipment } = profile(values);
    return Object.values(catalog).filter(item => item.equipment === "body" || item.equipment === equipment ||
      (equipment === "gym" && item.equipment === "dumbbell"));
  }

  function localDate(date = new Date()) {
    if (typeof date === "string" && /^\d{4}-\d{2}-\d{2}$/.test(date)) return date;
    const value = new Date(date);
    if (Number.isNaN(value.getTime())) return "";
    return [value.getFullYear(), value.getMonth() + 1, value.getDate()]
      .map(part => String(part).padStart(2, "0")).join("-");
  }

  function dateAtOffset(date, offset) {
    const day = new Date(`${localDate(date)}T12:00:00`);
    day.setDate(day.getDate() + offset);
    return localDate(day);
  }

  function positiveNumber(value) {
    const result = Number(value);
    return Number.isFinite(result) && result > 0 ? result : 0;
  }

  function setValue(set, exercise) {
    return positiveNumber(exercise.unit === "sec" ? set.seconds : set.reps);
  }

  function setsFor(workout, id) {
    return (workout.items || []).filter(item => item.id === id)
      .flatMap(item => Array.isArray(item.sets) ? item.sets.filter(Boolean) : []);
  }

  function latestPerformance(workouts, exercise) {
    const recent = workouts.find(workout => setsFor(workout, exercise.id).some(set => setValue(set, exercise)));
    if (!recent) return null;
    const savedSets = setsFor(recent, exercise.id).filter(set => setValue(set, exercise) > 0);
    const representative = savedSets.reduce((leading, set) => {
      const weight = positiveNumber(set.weight);
      const bestWeight = positiveNumber(leading.weight);
      if (weight !== bestWeight) return weight > bestWeight ? set : leading;
      return setValue(set, exercise) < setValue(leading, exercise) ? set : leading;
    });
    return {
      value: setValue(representative, exercise),
      weight: positiveNumber(representative.weight),
      effort: recent.effort,
      readiness: recent.readiness,
    };
  }

  function targetFor(item, settings, readiness, previous) {
    const step = item.unit === "sec" ? 5 : 1;
    const level = { beginner: 0, regular: 2, advanced: 4 }[settings.level];
    const goal = settings.goal === "endurance" ? 2 : 0;
    const starter = item.base + level * step + goal * step;
    const completed = previous?.value || starter;
    let target = Math.min(completed, starter);

    if (readiness !== "light" && previous) {
      target = completed;
      if (previous.readiness === "light") target = Math.min(starter, completed + step);
      else if (previous.effort === "easy" || previous.effort === "light" || readiness === "strong") target += step;
      if (previous.effort === "hard" || previous.effort === "heavy") target = completed - step;
    }
    if (readiness === "light") target *= 0.75;

    const lowerLimit = item.unit === "sec" ? 5 : 1;
    const upperLimit = item.unit === "sec" ? 90 : 24;
    return Math.max(lowerLimit, Math.min(upperLimit, Math.round(target / step) * step));
  }

  function plan(values, readiness = "normal", workouts = [], now = new Date()) {
    const settings = profile(values);
    let intensity = oneOf(labels.readiness, readiness, "normal");
    const today = localDate(now);
    const history = workouts.filter(workout => {
      const date = localDate(workout.date);
      return date && date <= today;
    }).sort((a, b) => new Date(b.date) - new Date(a.date));
    const rotation = schedules[settings.equipment];
    const trainingDates = new Set(history.map(workout => localDate(workout.date)).filter(Boolean));
    const loadedDates = new Set(history.filter(workout => workout.readiness !== "light")
      .map(workout => localDate(workout.date)));
    const needsRecovery = loadedDates.has(dateAtOffset(now, -1)) && loadedDates.has(dateAtOffset(now, -2)) &&
      !trainingDates.has(today);
    if (needsRecovery) intensity = "light";
    const isRecovery = intensity === "light";
    const minutes = isRecovery ? Math.min(20, settings.minutes) : settings.minutes;
    const count = { 10: 3, 20: 4, 30: 4, 45: 5 }[minutes];
    const ids = needsRecovery ? ["bridge", "birdDog", "proneY"] : rotation[trainingDates.size % rotation.length];
    const items = ids.slice(0, count).map(id => {
      const item = catalog[id];
      const previous = latestPerformance(history, item);
      const sets = isRecovery || minutes === 10 ? 1 : minutes === 20 ? 2 : 3;
      return {
        id,
        sets,
        target: targetFor(item, settings, intensity, previous),
        weight: item.load && !isRecovery ? (previous?.weight || 0) : 0,
      };
    });

    const title = needsRecovery ? "回復メニュー" : isRecovery ? "軽めの全身トレーニング" : "全身トレーニング";
    const first = catalog[items[0].id];
    const firstTarget = `${items[0].target}${first.unit === "sec" ? "秒" : "回"}`;
    const reason = needsRecovery ? "筋トレが2日続いたので、今日は回復を優先します。" :
      isRecovery ? "各種目を軽めに1セットずつ。" :
      history.length ? "前回の記録をもとに目標を調整しました。" : "脚・上半身・体幹を順に動かします。";
    const description = `${reason}まずは${first.name}を${firstTarget}。`;

    return {
      title,
      description,
      readiness: intensity,
      minutes,
      rest: isRecovery ? 45 : settings.goal === "strength" ? 90 : 60,
      isRecovery,
      items,
    };
  }

  function week(workouts = [], runs = [], now = new Date()) {
    const date = new Date(`${localDate(now)}T12:00:00`);
    const monday = -((date.getDay() + 6) % 7);
    const sessions = Array.from({ length: 7 }, (_, day) => ({
      date: dateAtOffset(now, monday + day),
      count: 0,
      minutes: 0,
      distance: 0,
    }));
    const daily = new Map(sessions.map(session => [session.date, session]));
    for (const workout of workouts) {
      const session = daily.get(localDate(workout.date));
      if (session) {
        session.count += 1;
        session.minutes += positiveNumber(workout.duration) / 60;
      }
    }
    for (const run of runs) {
      const session = daily.get(localDate(run.date));
      if (session) {
        session.count += 1;
        session.minutes += positiveNumber(run.duration) / 60;
        session.distance += positiveNumber(run.distance);
      }
    }
    return {
      count: sessions.filter(session => session.count > 0).length,
      minutes: Math.round(sessions.reduce((total, session) => total + session.minutes, 0)),
      sessions,
      distance: round(sessions.reduce((total, session) => total + session.distance, 0), 3),
    };
  }

  function round(value, decimals) {
    const factor = 10 ** decimals;
    return Math.round(value * factor) / factor;
  }

  function movementBests(workouts) {
    const exercises = {};
    for (const workout of workouts) {
      const volumes = {};
      for (const item of workout.items || []) {
        if (!Object.hasOwn(catalog, item.id)) continue;
        const movement = catalog[item.id];
        for (const set of item.sets || []) {
          if (!set) continue;
          const value = setValue(set, movement);
          if (!value) continue;
          const weight = movement.load ? positiveNumber(set.weight) : 0;
          const record = exercises[item.id] ||= { reps: 0, seconds: 0, weight: 0, volume: 0, repsByWeight: {} };
          if (movement.unit === "sec") record.seconds = Math.max(record.seconds, value);
          else record.reps = Math.max(record.reps, value);
          if (movement.load) {
            record.weight = Math.max(record.weight, weight);
            record.repsByWeight[weight] = Math.max(record.repsByWeight[weight] || 0, value);
            volumes[item.id] = (volumes[item.id] || 0) + weight * value;
          }
        }
      }
      for (const [id, volume] of Object.entries(volumes)) {
        exercises[id].volume = Math.max(exercises[id].volume, round(volume, 2));
      }
    }
    return exercises;
  }

  function runningBests(runs) {
    const result = { distance: 0, duration: 0, pace: 0 };
    for (const run of runs) {
      const distance = round(positiveNumber(run.distance), 3);
      const duration = positiveNumber(run.duration);
      result.distance = Math.max(result.distance, distance);
      result.duration = Math.max(result.duration, duration);
      if (distance >= 1 && duration > 0) {
        const pace = Math.round(duration / distance);
        if (pace > 0 && (!result.pace || pace < result.pace)) result.pace = pace;
      }
    }
    return result;
  }

  function bests(workouts = [], runs = []) {
    return { exercises: movementBests(workouts), run: runningBests(runs) };
  }

  function difference(label, previous, value, unit, lowerIsBetter = false) {
    if (!value) return [];
    const delta = previous ? round(lowerIsBetter ? previous - value : value - previous, 3) : null;
    return [{ label, previous: previous || null, value, delta, unit }];
  }

  function compareWorkout(workout, priorWorkouts = []) {
    const records = movementBests(priorWorkouts);
    const results = movementBests([workout]);
    return Object.entries(results).flatMap(([id, result]) => {
      const exercise = catalog[id];
      const previous = records[id];
      if (exercise.unit === "sec") return difference(exercise.name, previous?.seconds, result.seconds, "秒");
      if (!exercise.load || result.weight === 0) {
        return difference(exercise.name, exercise.load ? previous?.repsByWeight[0] : previous?.reps, result.reps, "回");
      }
      const bestWeight = result.weight;
      return [
        ...difference(`${exercise.name}・最高重量`, previous?.weight, bestWeight, "kg"),
        ...difference(`${exercise.name}（${bestWeight} kg）`, previous?.repsByWeight[bestWeight], result.repsByWeight[bestWeight], "回"),
      ];
    });
  }

  function compareRun(run, priorRuns = []) {
    const previous = runningBests(priorRuns);
    const value = runningBests([run]);
    return [
      ...difference("最長距離", previous.distance, value.distance, "km"),
      ...difference("最長時間", previous.duration, value.duration, "秒"),
      ...difference("最速ペース", previous.pace, value.pace, "秒/km", true),
    ];
  }

  const training = Object.freeze({
    available, bests, catalog, compareRun, compareWorkout, defaults, labels, localDate, plan, profile, week,
  });
  if (typeof module !== "undefined" && module.exports) module.exports = training;
  else globalThis.Training = training;
})();
