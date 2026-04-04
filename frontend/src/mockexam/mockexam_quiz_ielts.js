  // —— 字体缩放（仅作用于 .scalable 内容） ——
  let fontScale = 1.0, minScale = 0.75, maxScale = 3.0;
  function applyScale(){ document.documentElement.style.setProperty('--contentScale', (fontScale*100)+'%'); }
  function adjustFontSize(delta){ fontScale = Math.min(maxScale, Math.max(minScale, fontScale+delta)); applyScale(); }
  document.getElementById('decreaseFont').addEventListener('click',function(){adjustFontSize(-0.1);});
  document.getElementById('increaseFont').addEventListener('click',function(){adjustFontSize(0.1);});
  applyScale();

  // —— 全屏/暂停 ——
  var pauseOverlay=document.getElementById('pauseOverlay'), resumeBtn=document.getElementById('resumeBtn'), startOverlay=document.getElementById('startOverlay'), startBtn=document.getElementById('startBtn');
  var quizPaused=true; function requestFullScreen(){var el=document.documentElement; if(el.requestFullscreen)el.requestFullscreen(); else if(el.webkitRequestFullscreen)el.webkitRequestFullscreen(); else if(el.msRequestFullscreen)el.msRequestFullscreen();}
  document.addEventListener('fullscreenchange',function(){ if(!document.fullscreenElement){ /* 可选：暂停 */ } else { resumeQuiz(); } });
  function resumeQuiz(){ quizPaused=false; pauseOverlay.classList.remove('visible'); }
  startBtn.onclick=function(){ requestFullScreen(); setTimeout(function(){ startOverlay.style.display='none'; resumeQuiz(); if(examAPI.getCurrentIndex()==null&&examAPI.getLength()>0){ setCurrentQuestion(0,{scroll:false}); } },200); };
  resumeBtn.onclick=function(){ requestFullScreen(); };

  // —— 顶部时钟 ——
  var timerEl=document.getElementById('timer'); function updateTime(){var now=new Date(); var hh=String(now.getHours()).padStart(2,'0');var mm=String(now.getMinutes()).padStart(2,'0');var ss=String(now.getSeconds()).padStart(2,'0');timerEl.textContent=hh+':'+mm+':'+ss;} updateTime(); setInterval(updateTime,1000);

  const SAMPLE_DATA = {
    module: "TEST",
    passages: [
      {
        id: "P1",
        title: "READING PASSAGE 1",
        content: "<p>Fishbourne Roman Palace</p><p>The largest Roman villa in Britain</p><p>Fishbourne Roman Palace is in the village of Fishbourne in West Sussex, England. This large palace was built in the 1st century AD, around thirty years after the Roman conquest of Britain, on the site of Roman army grain stores which had been established after the invasion, in the reign of the Roman Emperor Claudius in 43 AD. The rectangular palace was built around formal gardens, the northern half of which have been reconstructed. There were extensive alterations in the 2nd and 3rd centuries AD, with many of the original black and white mosaic floors being overlaid with more sophisticated coloured ones, including a perfectly preserved mosaic of a dolphin in the north wing. More alterations were in progress when the palace burnt down in around 270 AD, after which it was abandoned.</p><p>Local people had long believed that a Roman palace once existed in the area. However, it was not until 1960 that the archaeologist Barry Cunliffe, of Oxford University, first systematically excavated the site, after workmen had accidentally uncovered a wall while they were laying a water main. The Roman villa excavated by Cunliffe's team was so grand that it became known as Fishbourne Roman Palace, and a museum was erected to preserve some of the remains. This is administered by the Sussex Archaeological Society.</p><p>In its day, the completed palace would have comprised four large wings with colonnaded fronts. The north and east wings consisted of suites of private rooms built around courtyards, with a monumental entrance in the middle of the east wing. In the north-east corner there was an assembly hall. The west wing contained state rooms, a large ceremonial reception room, and a gallery. The south wing contained the owner's private apartments. The palace included as many as 50 mosaic floors, under-floor central heating and a bathhouse. In size, Fishbourne Palace would have been approximately equivalent to some of the great Roman palaces of Italy, and was by far the largest known Roman residence north of the European Alps, at about 500 feet (150 m) square. A team of volunteers and professional archaeologists are involved in an ongoing archaeological excavation on the site of nearby, possibly military, buildings.</p><p>The first buildings to be erected on the site were constructed in the early part of the conquest in 43 AD. Later, two timber buildings were constructed, one with clay and mortar floors and plaster walls, which appears to have been a house of some comfort. These buildings were demolished in the 60s AD and replaced by a substantial stone house, which included colonnades, and a bath suite. It has been suggested that the palace itself, incorporating the previous house in its south-east corner, was constructed around 73-75 AD. However, Dr Miles Russell, of Bournemouth University, reinterpreted the ground plan and the collection of objects found and has suggested that, given the extremely close parallels with the imperial palace of Domitian in Rome, its construction may more plausibly date to after 92 AD.</p><p>With regard to who lived in Fishbourne Palace, there are a number of theories; for example, one proposed by Professor Cunliffe is that, in its early phase, the palace was the residence of Tiberius Claudius Cogidubnus, a local chieftain who supported the Romans, and who may have been installed as king of a number of territories following the first stage of the conquest. Cogidubnus is known from a reference to his loyalty in Agricola, a work by the Roman writer Tacitus, and from an inscription commemorating a temple dedicated to the gods Neptune and Minerva found in the nearby city of Chichester. Another theory is that it was built for Sallustius Lucullus, a Roman governor of Britain of the late 1st century, who may have been the son of the British prince Adminius. Two inscriptions recording the presence of Lucullus have been found in Chichester, and the redating by Miles Russell of the palace to the early 90s AD would fit far more securely with such an interpretation. If the palace was designed for Lucullus, then it may have only been in use for a few years, as the Roman historian Suetonius records that Lucullus was executed by the Emperor Domitian in or shortly after 93 AD.</p><p>Additional theories suggest that either Verica, a British king of the Roman Empire in the years preceding the Claudian invasion, was owner of the palace, or Tiberius Claudius Catuarus, following the recent discovery of a gold ring belonging to him. The palace outlasted the original owner, whoever he was, and was extensively re-planned early in the 2nd century AD, and subdivided into a series of lesser apartments. Further redevelopment was begun in the late 3rd century AD, but these alterations were incomplete when the north wing was destroyed in a fire in around 270 AD. The damage was too great to repair, and the palace was abandoned and later dismantled.</p><p>A modern museum has been built by the Sussex Archaeological Society, incorporating most of the visible remains, including one wing of the palace. The gardens have been re-planted using authentic plants from the Roman period.</p>",
        groups: [
          {
            id: "P1-G1",
            title: "Questions 1-6",
            type: "tfng",
            instructions: "<p>Do the following statements agree with the information given in Reading Passage 1?</p><p>In boxes 1-6 on your answer sheet, write</p><p>TRUE - if the statement agrees with the information</p><p>FALSE - if the statement contradicts the information</p><p>NOT GIVEN - if there is no information on this</p>",
            questions: [
              {
                id: "P1-Q1",
                stem: "<p>Fishbourne Palace was the first structure to be built on its site.</p>",
                answer: "FALSE"
              },
              {
                id: "P1-Q2",
                stem: "<p>Fishbourne Palace was renovated more than once.</p>",
                answer: "TRUE"
              },
              {
                id: "P1-Q3",
                stem: "<p>Fishbourne Palace was large in comparison with Roman palaces in Italy.</p>",
                answer: "TRUE"
              },
              {
                id: "P1-Q4",
                stem: "<p>Research is continuing in the area close to Fishbourne Palace.</p>",
                answer: "TRUE"
              },
              {
                id: "P1-Q5",
                stem: "<p>Researchers agree on the identity of the person for whom Fishbourne Palace was constructed.</p>",
                answer: "FALSE"
              },
              {
                id: "P1-Q6",
                stem: "<p>Fishbourne Palace was burnt down by local people.</p>",
                answer: "NOT GIVEN"
              }
            ]
          },
          {
            id: "P1-G2",
            title: "Questions 7-13",
            type: "cloze_inline",
            instructions: "<p>Complete the notes below.</p><p>Choose NO MORE THAN TWO WORDS AND/OR A NUMBER from the passage for each answer.</p><p>Write your answers in boxes 7-13 on your answer sheet.</p><p><strong>Fishbourne Palace</strong></p><p><strong>Possible inhabitants</strong> - Cogidubnus: he is named in several writings</p>",
            questions: [
              {
                content: "<p><strong>Fishbourne Palace</strong></p>\n<p><strong>Construction</strong></p>\n<ul>\n<li>the first buildings on the site contained food for the [[b7]]</li>\n<li>the palace building surrounded [[b8]]</li>\n<li>in the 2nd and 3rd centuries colour was added to the [[b9]] of the palace</li>\n</ul>\n<p><strong>Discovery</strong></p>\n<ul>\n<li>the first part of the palace to be found was a [[b10]]</li>\n</ul>\n<p><strong>Possible inhabitants</strong></p>\n<ul>\n<li>Cogidubnus – he is named in several writings</li>\n<li>Sallustius Lucullus – he may have lived there until approximately [[b11]] AD</li>\n<li>Verica – a British king</li>\n<li>Catuarus – his [[b12]] was found there</li>\n</ul>\n<p><strong>Present Day</strong></p>\n<ul>\n<li>a [[b13]] has been built on the site to help protect it</li>\n</ul>",
                blanks: [
                  { id: "b7", answer: "Roman army" },
                  { id: "b8", answer: "formal gardens" },
                  { id: "b9", answer: "mosaic floors" },
                  { id: "b10", answer: "wall" },
                  { id: "b11", answer: "93" },
                  { id: "b12", answer: "gold ring" },
                  { id: "b13", answer: "modern museum" }
                ]
              }
            ]
          }
        ]
      }
    ]
  };



  const answers = {};
  const marked = {};
  const questionMap = {};
  let examData = null;
  let QuestionStore = null;
  let idx = null;

  function clone(obj){ return JSON.parse(JSON.stringify(obj || {})); }
  function safeParseDataset(payload){
    if(payload===null||payload===undefined) return null;
    if(typeof payload==='string'){
      var trimmed=payload.trim();
      if(!trimmed||trimmed==='null'||trimmed==='undefined') return null;
      try{
        var parsed=JSON.parse(trimmed);
        if(typeof parsed==='string'){
          var inner=parsed.trim();
          if(inner && (inner[0]==='{' || inner[0]==='[')){
            try{ return JSON.parse(inner); }catch(_e){}
          }
        }
        return parsed;
      }catch(err){ console.warn('无法解析题目数据',err); return null; }
    }
    if(typeof payload==='object'){
      try{ return JSON.parse(JSON.stringify(payload)); }catch(err){ return payload; }
    }
    return null;
  }
  function getGroupList(passage){
    if(!passage || typeof passage!=='object') return [];
    if(Array.isArray(passage.groups)) return passage.groups;
    if(Array.isArray(passage.questionGroups)) return passage.questionGroups;
    if(Array.isArray(passage.sets)) return passage.sets;
    return [];
  }
  function getGroupQuestions(group){
    if(!group || typeof group!=='object') return [];
    if(Array.isArray(group.questions)) return group.questions;
    if(Array.isArray(group.items)) return group.items;
    return [];
  }
  function resolveTFNGFromOptions(answer, options){
    var raw=normalizeLabel(answer);
    if(!raw) return '';
    var opts=parseOptions(options);
    var match=opts.find(function(opt){ return normalizeLabel(opt.label)===raw; });
    if(!match) return '';
    var text=normalizeText(match.content || match.label || '');
    return normalizeTFNGValue(text);
  }
  function normalizeTFNGAnswer(question){
    if(!question || question.type!=='tfng') return;
    var direct=normalizeTFNGValue(question.answer);
    if(direct){ question.answer=direct; return; }
    var mapped='';
    if(question.options){ mapped=resolveTFNGFromOptions(question.answer, question.options); }
    if(!mapped){
      var raw=normalizeLabel(question.answer);
      if(raw==='A') mapped='TRUE';
      else if(raw==='B') mapped='FALSE';
      else if(raw==='C') mapped='NOT GIVEN';
    }
    if(mapped){ question.answer=mapped; }
  }
  function normalizeQuestion(question, context){
    var normalized = Object.assign({}, question);
    var typeHint = (normalized.type || context.groupType || '').toLowerCase();
    var optionHint = normalized.options || normalized.answerOptions || context.groupOptions;
    normalized.type = typeHint || (optionHint ? 'single' : 'blank');
    normalized.id = normalized.id || (context.passageId+'-Q'+(context.localIndex+1));
    normalized.stem = normalized.stem || normalized.question || normalized.text || normalized.statement || '';
    if(!normalized.stem && normalized.type!=='cloze_inline'){
      normalized.stem = normalized.content || '';
    }
    normalized.options = normalized.options || normalized.answerOptions || context.groupOptions || null;
    if(context.groupChoices && !normalized.choices){ normalized.choices = context.groupChoices; }
    if(normalized.type==='cloze_inline'){
      var inlineContent = normalized.content || '';
      if(!inlineContent){
        var stemText = String(normalized.stem||'');
        if(stemText.indexOf('{' + '{')!==-1 || stemText.indexOf('[' + '[')!==-1){
          inlineContent = stemText;
          normalized.stem = '';
        }
      }
      normalized.content = inlineContent;
      if(!Array.isArray(normalized.blanks) || normalized.blanks.length===0){
        var blanks=[];
        var re=/\{\{\s*([^}]+)\s*\}\}|\[\[\s*([^\]]+)\s*\]\]/g; var m;
        while((m=re.exec(inlineContent))!==null){ blanks.push({ id: (m[1]||m[2]||'').trim(), answer: '' }); }
        normalized.blanks = blanks;
      }
    }
    normalized.passageId = context.passageId;
    normalized.passageTitle = context.passageTitle;
    normalized.passageIndex = context.passageIndex;
    normalized.localIndex = context.localIndex;
    normalized.section = normalized.section || context.section;
    normalized.difficulty = normalized.difficulty || context.groupDifficulty || context.passageDifficulty || 'Standard';
    normalized.groupId = context.groupId || null;
    normalized.groupTitle = context.groupTitle || '';
    normalized.groupInstructions = context.groupInstructions || '';
    normalized.groupIndex = context.groupIndex;
    normalized.groupLocalIndex = context.groupLocalIndex;
    if(answers[normalized.id]===undefined){ answers[normalized.id] = normalized.type==='multiple' ? [] : null; }
    if(marked[normalized.id]===undefined){ marked[normalized.id] = false; }
    if(normalized.type==='tfng'){ normalizeTFNGAnswer(normalized); }
    return normalized;
  }
  function convertLegacyPayload(legacy){
    var reserved={module:true,passages:true};
    var passages=[];
    if(!legacy||typeof legacy!=='object') return passages;
    for(var section in legacy){
      if(!Object.prototype.hasOwnProperty.call(legacy,section)||reserved[section]) continue;
      var group=legacy[section];
      if(!group||typeof group!=='object') continue;
      for(var difficulty in group){
        if(!Object.prototype.hasOwnProperty.call(group,difficulty)) continue;
        var qArr = Array.isArray(group[difficulty]) ? group[difficulty] : [];
        if(!qArr.length) continue;
        passages.push({ id: section+'-'+difficulty, title: section+' '+difficulty, content: (qArr[0]&&qArr[0].stimulus)||'', questions: qArr });
      }
    }
    return passages;
  }
  function normalizeExamData(payload){
    var parsed=safeParseDataset(payload);
    var base=parsed;
    if(!base||!Array.isArray(base.passages)||base.passages.length===0){
      var legacyPassages=convertLegacyPayload(base);
      if(legacyPassages.length){
        base={ module:(base&&base.module)||'Reading', passages:legacyPassages };
      } else {
        base=clone(SAMPLE_DATA);
      }
    }
    var moduleName = base.module || SAMPLE_DATA.module || 'Reading';
    var normalizedPassages = (base.passages||[]).map(function(passage,pIndex){
      var passageId = passage.id || ('P'+(pIndex+1));
      var title = passage.title || ('Passage '+(pIndex+1));
      var content = passage.content || passage.stimulus || '';
      var audio = passage.audio || passage.audioUrl || passage.audio_url || '';
      var instructions = passage.instructions || passage.instruction || '';
      var groupList = getGroupList(passage);
      var normalizedGroups = [];
      var flatQuestions = [];
      var localIndex = 0;
      if(groupList.length){
        normalizedGroups = groupList.map(function(group,gIndex){
          var groupId = group.id || (passageId+'-G'+(gIndex+1));
          var groupTitle = group.title || group.name || group.label || '';
          var groupInstructions = group.instructions || group.instruction || group.stem || '';
          var groupType = (group.type || '').toLowerCase();
          var groupOptions = group.options || group.answerOptions || null;
          var groupChoices = group.choices || null;
          var groupQuestions = getGroupQuestions(group);
          var normalizedGroupQuestions = groupQuestions.map(function(question,qIndex){
            var normalized = normalizeQuestion(question, {
              passageId: passageId,
              passageTitle: title,
              passageIndex: pIndex,
              passageDifficulty: passage.difficulty,
              section: moduleName,
              groupId: groupId,
              groupTitle: groupTitle,
              groupInstructions: groupInstructions,
              groupIndex: gIndex,
              groupLocalIndex: qIndex,
              groupType: groupType,
              groupOptions: groupOptions,
              groupChoices: groupChoices,
              groupDifficulty: group.difficulty,
              localIndex: localIndex
            });
            localIndex += 1;
            flatQuestions.push(normalized);
            return normalized;
          });
          return {
            id: groupId,
            title: groupTitle,
            instructions: groupInstructions,
            type: groupType,
            options: groupOptions,
            choices: groupChoices,
            index: gIndex,
            questions: normalizedGroupQuestions
          };
        }).filter(function(group){ return group.questions.length>0; });
      }
      if(!flatQuestions.length){
        var questions = Array.isArray(passage.questions) ? passage.questions : [];
        questions.forEach(function(question,qIndex){
          var normalized = normalizeQuestion(question, {
            passageId: passageId,
            passageTitle: title,
            passageIndex: pIndex,
            passageDifficulty: passage.difficulty,
            section: moduleName,
            groupId: null,
            groupTitle: '',
            groupInstructions: '',
            groupIndex: null,
            groupLocalIndex: qIndex,
            groupType: '',
            groupOptions: null,
            groupChoices: null,
            groupDifficulty: null,
            localIndex: localIndex
          });
          localIndex += 1;
          flatQuestions.push(normalized);
        });
      }
      var hasGroups = normalizedGroups && normalizedGroups.length;
      return {
        id: passageId,
        title: title,
        content: content,
        audio: audio,
        instructions: instructions,
        index: pIndex,
        questions: flatQuestions,
        groups: hasGroups ? normalizedGroups : null
      };
    }).filter(function(p){ return p.questions.length>0; });
    if(!normalizedPassages.length){
      return normalizeExamData(clone(SAMPLE_DATA));
    }
    return { module: moduleName, passages: normalizedPassages };
  }

  function buildQuestionStore(data){
    var pool=[];
    data.passages.forEach(function(passage){
      passage.questions = passage.questions.map(function(question){
        question.globalIndex = pool.length;
        question.displayNo = pool.length + 1;
        questionMap[question.id] = question;
        pool.push(question);
        return question;
      });
    });
    return {
      get length(){ return pool.length; },
      get: function(index){ return pool[index]; }
    };
  }

  var bootstrap = window.QUIZ_BOOTSTRAP || {};
  var rawData = bootstrap.items || null;
  var QUIZ_SESSION_ID = bootstrap.sessionId || null;
  var QUIZ_STARTED_AT = bootstrap.sessionCreatedAt || null;
  var QUESTION_BANK_ID = bootstrap.questionBankId || null;
  var QUIZ_SOURCE_TYPE = bootstrap.sourceType || 'question-bank';
  var QUIZ_SOURCE_ID = bootstrap.sourceId || QUESTION_BANK_ID || null;
  var SUBMIT_URL = bootstrap.submitUrl || (QUESTION_BANK_ID ? '/api/v1/mockexam/question-banks/' + QUESTION_BANK_ID + '/submit' : '');
  var INLINE_PAYLOAD = bootstrap.inlinePayload || null;
  var quizSubmitting = false;
  var quizSubmitted = false;

  function decryptAESCBC(encryptedMessage, key) {
      if (key === void 0) key = "rcF3FMdBGh0XF7kQoL16DsswTYs78byL";
      var iv = "Wc8rNpAYb3Xz7D2Q";
      if (!encryptedMessage) {
          alert("解密失败：请联系管理员");
          return "";
      }
      var keyWords = CryptoJS.enc.Utf8.parse(key);
      var ivWords = CryptoJS.enc.Utf8.parse(iv);
      var decrypted = CryptoJS.AES.decrypt(
              {ciphertext: CryptoJS.enc.Base64.parse(encryptedMessage)},
              keyWords,
              {
                  iv: ivWords,
                  mode: CryptoJS.mode.CBC,
                  padding: CryptoJS.pad.ZeroPadding,
              }
      );
      return decrypted.toString(CryptoJS.enc.Utf8);
  }

  function resolveBootstrapExamData(payload) {
    if(payload==null){ return null; }
    if(typeof payload==='object' && payload.d){
      try{
        return JSON.parse(decryptAESCBC(payload.d));
      }catch(err){
        console.warn('加密题库解密失败，尝试使用原始数据', err);
      }
    }
    if(typeof payload==='string'){
      try{ return JSON.parse(payload); }catch(_err){ return payload; }
    }
    return payload;
  }

  examData = normalizeExamData(resolveBootstrapExamData(rawData));
  QuestionStore = buildQuestionStore(examData);
  idx = QuestionStore.length ? 0 : null;

  window.examAPI = {
    goToQuestion: function(i){ setCurrentQuestion(i); },
    getCurrentIndex: function(){ return idx; },
    getLength: function(){ return QuestionStore ? QuestionStore.length : 0; },
    getQuestion: function(i){ if(!QuestionStore||i==null||i<0||i>=QuestionStore.length) return null; return QuestionStore.get(i); },
    getById: function(id){ return questionMap[id]||null; }
  };

  const TFNG_CHOICES=[
    { value:'TRUE', label:'TRUE' },
    { value:'FALSE', label:'FALSE' },
    { value:'NOT GIVEN', label:'NOT GIVEN' }
  ];
  let answersVisible=false;
  var sectionLabel=document.getElementById('sectionLabel');
  var questionPane=document.getElementById('questionPane');
  var passageContainer=document.getElementById('passageContainer');
  var stimulusPane=document.getElementById('stimulusPane');
  var stimulusModule=document.getElementById('stimulusModule');
  var stimulusTitle=document.getElementById('stimulusTitle');
  var stimulusInstructions=document.getElementById('stimulusInstructions');
  var stimulusContent=document.getElementById('stimulusContent');
  var questionAudioWrap=document.getElementById('questionAudioWrap');
  var questionAudio=document.getElementById('questionAudio');
  var prevBtn=document.getElementById('prevBtn');
  var actionBtn=document.getElementById('actionBtn');
  var qBadge=document.getElementById('qBadge');
  var markBtn=document.getElementById('markBtn');
  var markIcon=document.getElementById('markIcon');
  var lockBadge=document.getElementById('lockBadge');
  var progressEl=document.getElementById('progress');
  var navOverlay=document.getElementById('navOverlay');
  var navBackdrop=document.getElementById('navBackdrop');
  var navBody=document.getElementById('navBody');
  var navClose=document.getElementById('navClose');
  var toggleAnswersBtn=document.getElementById('toggleAnswers');
  var activePassageId=null;
  var passageObserver=null;

  function normalizeText(htmlOrText){ var div=document.createElement('div'); div.innerHTML=String(htmlOrText||''); var text=div.textContent||div.innerText||''; return text.replace(/\s+/g,' ').trim(); }
  function getAudioUrl(passage){
    if(!passage) return '';
    var url = passage.audio || passage.audioUrl || passage.audio_url || '';
    return String(url || '').trim();
  }
  function findCorrectIndex(opts,answer){ var ansRaw=String(answer||'').trim(); if(!ansRaw) return -1; var asLabel=ansRaw.toUpperCase(); for(var i=0;i<opts.length;i++){ if(opts[i].label===asLabel) return i; }
    var ansNorm=normalizeText(ansRaw); for(var j=0;j<opts.length;j++){ if(normalizeText(opts[j].content)===ansNorm) return j; } return -1; }
  function parseOptions(raw){
    var arr=[];
    if(raw==null){ arr=[]; }
    else if(typeof raw==='string'){
      var s=raw.trim();
      if(!s||s.toLowerCase()==='null'){ arr=[]; }
      else{ try{ var parsed=JSON.parse(s); if(Array.isArray(parsed)){ arr=parsed; } else if(parsed && Array.isArray(parsed.options)){ arr=parsed.options; } else { arr=[]; } } catch(e){ arr=[]; } }
    }
    else if(Array.isArray(raw)){ arr=raw; }
    else if(typeof raw==='object'&&Array.isArray(raw.options)){ arr=raw.options; }
    else { arr=[]; }
    var out=[];
    for(var i=0;i<arr.length;i++){
      var item=arr[i];
      if(item==null) continue;
      var content='';
      var label='';
      if(typeof item==='object'){
        content=item.content!=null?item.content:item.text!=null?item.text:item.value!=null?item.value:item.label!=null?item.label:item.Content!=null?item.Content:'';
        label=item.label!=null?String(item.label):item.value!=null?String(item.value):'';
      } else {
        content=String(item);
      }
      if(typeof content==='string'){ content=content.trim(); }
      if(!label){ label=String.fromCharCode(65+out.length); }
      if(!content) continue;
      out.push({ label:label.trim(), content:content, display: item && item.display ? item.display : label.trim() });
    }
    return out;
  }
  function normalizeLabel(value){ return String(value||'').trim().toUpperCase(); }
  function escapeAttr(value){
    return String(value||'')
      .replace(/&/g,'&amp;')
      .replace(/\"/g,'&quot;')
      .replace(/</g,'&lt;')
      .replace(/>/g,'&gt;');
  }
  function getMatchingItems(question){
    if(!question) return [];
    if(Array.isArray(question.questions)) return question.questions;
    if(Array.isArray(question.items)) return question.items;
    return [];
  }
  function getMatchingAnswer(question,itemId){
    var map=answers[question.id];
    if(!map || typeof map!=='object' || Array.isArray(map)) return '';
    return map[itemId] || '';
  }
  function setMatchingAnswer(question,itemId,label){
    if(!answers[question.id] || typeof answers[question.id]!=='object' || Array.isArray(answers[question.id])){ answers[question.id]={}; }
    answers[question.id][itemId]=label;
  }
  function getCorrectForBlank(question,blankId){
    if(!question || !Array.isArray(question.blanks)) return '';
    var item=question.blanks.find(function(b){ return b.id===blankId; });
    return item ? String(item.answer||'') : '';
  }
  function isMatchingCorrect(question,itemId,label){
    var items=getMatchingItems(question);
    var item=items.find(function(it){ return String(it.id)===String(itemId); });
    if(!item) return false;
    return normalizeLabel(item.answer)===normalizeLabel(label);
  }

  function normalizeTFNGValue(value){
    var text=String(value||'').trim().toUpperCase();
    if(text==='T'||text==='TRUE') return 'TRUE';
    if(text==='F'||text==='FALSE') return 'FALSE';
    if(text==='NG'||text==='NOT GIVEN'||text==='NOTGIVEN') return 'NOT GIVEN';
    return '';
  }

  function getTFNGChoices(question){
    if(question && Array.isArray(question.choices) && question.choices.length){
      return question.choices.map(function(choice){
        if(typeof choice==='string'){ return { value: normalizeTFNGValue(choice), label: normalizeTFNGValue(choice) }; }
        var value=normalizeTFNGValue(choice.value||choice.label||'');
        var label=String(choice.label||choice.value||value||'').toUpperCase();
        return { value:value, label:label };
      }).filter(function(choice){ return choice.value; });
    }
    if(question && question.allowNG===false){
      return [
        { value:'TRUE', label:'TRUE' },
        { value:'FALSE', label:'FALSE' }
      ];
    }
    return TFNG_CHOICES.slice();
  }

  function getOptions(question){
    if(!question) return [];
    if(question.__optionsCache) return question.__optionsCache;
    if(question.type==='tfng'){
      question.__optionsCache = getTFNGChoices(question).map(function(choice){ return Object.assign({}, choice); });
      return question.__optionsCache;
    }
    question.__optionsCache = parseOptions(question.options);
    return question.__optionsCache;
  }

  function getCorrectLabels(question){
    if(!question) return [];
    if(question.__correctCache) return question.__correctCache;
    var labels=[];
    if(question.type==='multiple'){
      labels=normalizeMultiAnswer(question.answer);
    } else if(question.type==='tfng'){
      var tf=normalizeTFNGValue(question.answer);
      labels=tf?[tf]:[];
    } else if(question.type==='single'){
      var opts=getOptions(question);
      var idx=findCorrectIndex(opts,question.answer);
      if(idx!==-1){ labels=[opts[idx].label]; }
      else{
        var fallback=String(question.answer||'').trim();
        if(fallback) labels=[fallback];
      }
    } else if(question.type==='blank' || question.type==='essay'){
      var text=String(question.answer||question.modelAnswer||'').trim();
      labels=text?[text]:[];
    } else {
      var raw=String(question.answer||'').trim();
      if(raw) labels=[raw];
    }
    question.__correctCache = labels;
    return labels;
  }

  function shouldRevealAnswers(){ return answersVisible; }
  function isCorrectChoice(question,label){
    if(question.type==='tfng'){
      return getCorrectLabels(question).includes(normalizeTFNGValue(label));
    }
    return getCorrectLabels(question).includes(label);
  }

  function hasAnswer(qId, question){
    var q = question || questionMap[qId];
    var val = answers[qId];
    if(q && q.type==='matching'){
      var items=getMatchingItems(q);
      if(!items.length || !val || typeof val!=='object' || Array.isArray(val)) return false;
      return items.every(function(item){ var v=val[item.id]; return v!==null && v!==undefined && String(v).trim()!==''; });
    }
    if(q && q.type==='cloze_inline'){
      var blanks=Array.isArray(q.blanks)?q.blanks:[];
      if(!blanks.length || !val || typeof val!=='object' || Array.isArray(val)) return false;
      return blanks.every(function(blank){ var v=val[blank.id]; return v!==null && v!==undefined && String(v).trim()!==''; });
    }
    if(Array.isArray(val)) return val.length>0;
    return val!==null && val!==undefined && String(val).trim()!=='';
  }
  function ensureDefaultAnswer(question){
    if(!question) return;
    if(answers[question.id] !== undefined) return;
    if(question.type==='multiple'){
      answers[question.id]=[];
      return;
    }
    if(question.type==='blank' || question.type==='essay'){
      answers[question.id]='';
      return;
    }
    if(question.type==='cloze_inline' || question.type==='matching'){
      answers[question.id]={};
      return;
    }
    if(question.type==='tfng'){
      answers[question.id]=null;
      return;
    }
    if(question.type==='single'){
      answers[question.id]=null;
      return;
    }
    answers[question.id]=null;
  }
  function normalizeMultiAnswer(answer){ var list=[]; if(Array.isArray(answer)){ list=answer.slice(); } else if(typeof answer==='string'){ list=answer.split(/[,;|]/); } return list.map(function(token){ return String(token||'').trim().toUpperCase(); }).filter(Boolean); }
  function isLocked(question){ return !!(question && question.locked); }
  function isInteractionLocked(question){ return isLocked(question) || shouldRevealAnswers(); }
  function isChoiceSelected(question,label){
    var value=answers[question.id];
    if(question.type==='tfng'){ return normalizeTFNGValue(value)===normalizeTFNGValue(label); }
    if(Array.isArray(value)) return value.includes(label);
    return value===label;
  }
  function toggleMultiAnswer(question,label){ var current=Array.isArray(answers[question.id])?answers[question.id].slice():[]; var idx=current.indexOf(label); if(idx===-1){ current.push(label); } else { current.splice(idx,1); } answers[question.id]=current; }

  function renderExam(){
    if(!passageContainer) return;
    questionPane.classList.remove('centered');
    if(!examData || !examData.passages.length){
      passageContainer.innerHTML='<p class="text-center text-slate-500">暂无可显示的题目。</p>';
      qBadge.textContent='-';
      sectionLabel.textContent='';
      prevBtn.disabled=actionBtn.disabled=markBtn.disabled=true;
      updateProgress();
      renderStimulusPane(null);
      return;
    }
    if(!activePassageId){ activePassageId = examData.passages[0].id; }
    var activePassage=getPassageById(activePassageId);
    renderStimulusPane(activePassage);
    renderActivePassageQuestions(activePassage);
    updateProgress();
  }

  function setActivePassage(passageId, opts){
    if(!passageId) return;
    if(!opts || opts.force !== true){
      if(activePassageId === passageId) return;
    }
    activePassageId = passageId;
    var target = getPassageById(activePassageId);
    renderStimulusPane(target);
    renderActivePassageQuestions(target);
  }

  function getGroupDisplayTitle(group){
    if(!group) return '';
    var base=group.title || group.name || '';
    if(base) return base;
    var list=Array.isArray(group.questions)?group.questions:[];
    if(!list.length) return '';
    var first=list[0].displayNo || list[0].order || '';
    var last=list[list.length-1].displayNo || first;
    if(!first) return '';
    if(first===last) return 'Question '+first;
    return 'Questions '+first+'-'+last;
  }

  function buildGroupBlock(group){
    var block=document.createElement('section');
    block.className='question-group';
    block.dataset.groupId=group.id || '';
    var header=document.createElement('div');
    header.className='group-header';
    var titleText=getGroupDisplayTitle(group);
    if(titleText){
      var title=document.createElement('div');
      title.className='group-title';
      title.textContent=titleText;
      header.appendChild(title);
    }
    if(group.type){
      var meta=document.createElement('div');
      meta.className='group-meta';
      meta.textContent=String(group.type || '').toUpperCase();
      header.appendChild(meta);
    }
    if(header.childNodes.length){ block.appendChild(header); }
    if(group.instructions){
      var instructions=document.createElement('div');
      instructions.className='group-instructions scalable content-reset';
      instructions.innerHTML=group.instructions;
      block.appendChild(instructions);
    }
    var qList=document.createElement('div');
    qList.className='mt-4 flex flex-col gap-4';
    (group.questions||[]).forEach(function(question){
      ensureDefaultAnswer(question);
      qList.appendChild(buildQuestionCard(question));
    });
    block.appendChild(qList);
    return block;
  }

  function renderActivePassageQuestions(passage){
    if(!passageContainer) return;
    var target=passage || getPassageById(activePassageId);
    passageContainer.innerHTML='';
    if(!target){
      passageContainer.innerHTML='<p class="text-center text-slate-500">未找到对应题组。</p>';
      return;
    }
    // var header=document.createElement('header');
    // header.className='border-b pb-3 mb-4';
    // header.innerHTML='<p class="text-xs uppercase tracking-wide text-slate-500">'+(examData.module||'Reading')+'</p><h2 class="text-lg font-semibold">'+target.title+'</h2>'+(target.instructions?'<p class="text-sm text-slate-600 mt-1">'+target.instructions+'</p>':'');
    // passageContainer.appendChild(header);
    if(Array.isArray(target.groups) && target.groups.length){
      target.groups.forEach(function(group){
        passageContainer.appendChild(buildGroupBlock(group));
      });
    } else {
      var qList=document.createElement('div');
      qList.className='flex flex-col gap-4';
      target.questions.forEach(function(question){ ensureDefaultAnswer(question); qList.appendChild(buildQuestionCard(question)); });
      passageContainer.appendChild(qList);
    }
    MathJax.typesetPromise([questionPane]).catch(function(err){console.error(err);});
  }

  function buildQuestionCard(question){
     var card=document.createElement('article');
     card.className='question-card';
     card.id='q-'+question.id;
     card.dataset.qid=question.id;
     card.dataset.index=question.globalIndex;
     var header=document.createElement('div');
     header.className='flex items-center justify-between gap-3 mb-3';
     header.innerHTML='<div class="flex items-center gap-3"><span class="badge">'+question.displayNo+'</span><span class="scalable content-reset question-stem">'+question.stem+'</span></div><span class="mark-pill hidden">已标记</span>';
     card.appendChild(header);
    if(question.type==='blank'){
      var input=document.createElement('textarea');
      input.rows=2;
      input.className='w-full border rounded-lg p-3 scalable';
      input.value=answers[question.id]||'';
      input.disabled=isInteractionLocked(question);
      if(shouldRevealAnswers()){
        var wrong = answers[question.id] && getAnswerText(question) && String(answers[question.id]).trim() !== String(getAnswerText(question)).trim();
        if(wrong){ input.classList.add('input-wrong'); }
      }
      input.placeholder='请输入答案';
      input.addEventListener('input',function(e){ answers[question.id]=e.target.value; updateProgress(); refreshQuestionCard(question.id); });
      input.addEventListener('focus',function(){ setCurrentQuestion(question.globalIndex,{scroll:false}); });
      card.appendChild(input);
    } else if(question.type==='cloze_inline'){
      card.appendChild(buildClozeInline(question));
    } else if(question.type==='essay'){
      var essayWrap=document.createElement('div');
      essayWrap.className='space-y-2';
      var textarea=document.createElement('textarea');
      textarea.className='w-full border rounded-lg p-3 scalable essay-input';
      textarea.placeholder='Write your response here...';
      textarea.value=answers[question.id]||'';
      textarea.disabled=isInteractionLocked(question);
      textarea.addEventListener('input',function(e){ answers[question.id]=e.target.value; updateEssayCounter(essayWrap,e.target.value,question.wordLimit); updateProgress(); });
      textarea.addEventListener('focus',function(){ setCurrentQuestion(question.globalIndex,{scroll:false}); });
      essayWrap.appendChild(textarea);
      var counter=document.createElement('div');
      counter.className='text-xs text-slate-500';
      counter.dataset.role='essayCounter';
      essayWrap.appendChild(counter);
      updateEssayCounter(essayWrap,textarea.value,question.wordLimit);
      card.appendChild(essayWrap);
    } else if(question.type==='matching'){
      card.appendChild(buildMatchingBlock(question));
    } else if(question.type==='tfng'){
      card.appendChild(buildTFNGOptions(question));
    } else {
      var opts=getOptions(question);
      var optWrap=document.createElement('div');
      optWrap.className='space-y-3';
      opts.forEach(function(opt){ optWrap.appendChild(buildOptionButton(question,opt)); });
      card.appendChild(optWrap);
    }
     card.addEventListener('click',function(e){ if(e.target.closest('button.option-btn')||e.target.closest('textarea')||e.target.closest('input')||e.target.closest('button.tfng-option')) return; setCurrentQuestion(question.globalIndex,{scroll:false}); });
     updateMarkIndicator(question.id);
     var panel=buildAnswerPanel(question);
     if(panel){ card.appendChild(panel); }
     return card;
   }

  function buildClozeInline(question){
     var wrapper=document.createElement('div');
     wrapper.className='scalable content-reset';
     var content=String(question.content||question.stem||'');
     var regex=/\{\{\s*([^}]+)\s*\}\}|\[\[\s*([^\]]+)\s*\]\]/g;
     var html=content.replace(regex,function(_m,a,b){
       var blankId=(a||b||'').trim();
       return '<input type="text" class="blank-input" data-blank="'+escapeAttr(blankId)+'" />' +
         '<span class="blank-solution" data-blank-solution="'+escapeAttr(blankId)+'" aria-hidden="true"></span>';
     });
     wrapper.innerHTML=html || '暂无题干内容';
     wrapper.querySelectorAll('input.blank-input').forEach(function(input){
       var blankId=input.dataset.blank;
       input.value=(answers[question.id]&&answers[question.id][blankId])||'';
       input.disabled=isInteractionLocked(question);
       if(shouldRevealAnswers()){
         var correct=getCorrectForBlank(question,blankId);
         if(correct && normalizeLabel(input.value)===normalizeLabel(correct)){ input.classList.add('option-correct'); }
         else if(input.value){ input.classList.add('option-wrong'); }
       }
       input.addEventListener('input',function(e){
         var id=e.target.dataset.blank;
         if(!answers[question.id] || typeof answers[question.id]!=='object' || Array.isArray(answers[question.id])){ answers[question.id]={}; }
         answers[question.id][id]=e.target.value;
        updateProgress();
        refreshQuestionCard(question.id);
      });
    });
     wrapper.querySelectorAll('[data-blank-solution]').forEach(function(span){
       var blankId=span.getAttribute('data-blank-solution');
       var correct=getCorrectForBlank(question,blankId);
       span.textContent='';
       if(shouldRevealAnswers() && correct){
         var userVal=(answers[question.id]&&answers[question.id][blankId])||'';
         var isCorrect=normalizeLabel(userVal)===normalizeLabel(correct);
         if(!isCorrect){ span.textContent=correct; }
       }
     });
     return wrapper;
   }

  function buildMatchingBlock(question){
     var wrapper=document.createElement('div');
     wrapper.className='matching-block';
     var options=getOptions(question);
     if(options.length){
       var optionBox=document.createElement('div');
       optionBox.className='matching-options';
       options.forEach(function(opt){
         var line=document.createElement('div');
         line.className='matching-option-line';
         var label=document.createElement('div');
         label.className='label';
         label.textContent=opt.label;
         var body=document.createElement('div');
         body.className='content-reset';
         body.innerHTML=opt.content;
         line.appendChild(label);
         line.appendChild(body);
         optionBox.appendChild(line);
       });
       wrapper.appendChild(optionBox);
     }
     var items=getMatchingItems(question);
     var rows=document.createElement('div');
     rows.className='matching-rows';
     if(!items.length){
       rows.innerHTML='<p class="text-slate-500">暂无可显示的匹配题。</p>';
       wrapper.appendChild(rows);
       return wrapper;
     }
     items.forEach(function(item){
       var row=document.createElement('div');
       row.className='matching-row';
       row.dataset.itemId=item.id;
       var prompt=document.createElement('div');
       prompt.className='prompt scalable content-reset';
       prompt.innerHTML='<strong>'+item.id+'</strong> '+(item.text||item.stem||'');
       row.appendChild(prompt);
       var btnWrap=document.createElement('div');
       btnWrap.className='matching-buttons';
       options.forEach(function(opt){
         var btn=document.createElement('button');
         btn.type='button';
         btn.className='matching-btn';
         btn.dataset.choice=opt.label;
         btn.dataset.itemId=item.id;
         btn.textContent=opt.label;
         var selected=normalizeLabel(getMatchingAnswer(question,item.id))===normalizeLabel(opt.label);
         if(selected){ btn.classList.add('choice-selected'); }
         if(shouldRevealAnswers()){
           if(isMatchingCorrect(question,item.id,opt.label)){ btn.classList.add('option-correct'); }
           if(selected && !isMatchingCorrect(question,item.id,opt.label)){ btn.classList.add('option-wrong'); }
         }
         btn.disabled=isInteractionLocked(question);
         btn.addEventListener('click',function(e){
           e.preventDefault();
           if(isInteractionLocked(question)) return;
           setMatchingAnswer(question,item.id,opt.label);
           refreshQuestionCard(question.id);
           updateProgress();
         });
         btnWrap.appendChild(btn);
       });
       row.appendChild(btnWrap);
       rows.appendChild(row);
     });
     wrapper.appendChild(rows);
     return wrapper;
   }

  function buildOptionButton(question,opt){
     var btn=document.createElement('button');
     btn.type='button';
     btn.className='option-btn w-full text-left border rounded-xl px-4 py-3 transition shadow-sm';
     btn.dataset.qid=question.id;
     btn.dataset.choice=opt.label;
     btn.innerHTML='<div class="scalable flex items-center gap-3"><span class="choice-radio">'+opt.label+'</span><div class="option-html">'+opt.content+'</div></div>';
     if(isChoiceSelected(question,opt.label)){ btn.classList.add('choice-selected'); } else { btn.classList.add('border-slate-200','hover:border-slate-400'); }
     if(shouldRevealAnswers()){
       if(isCorrectChoice(question,opt.label)){ btn.classList.add('option-correct'); }
       if(isChoiceSelected(question,opt.label) && !isCorrectChoice(question,opt.label)){ btn.classList.add('option-wrong'); }
     }
     if(isInteractionLocked(question)){ btn.disabled=true; }
     btn.addEventListener('click',function(e){ e.preventDefault(); if(isInteractionLocked(question)) return; if(question.type==='multiple'){ toggleMultiAnswer(question,opt.label); } else { answers[question.id]=opt.label; } refreshQuestionCard(question.id); updateProgress(); setCurrentQuestion(question.globalIndex,{scroll:false}); });
     return btn;
   }
 
  function buildTFNGOptions(question){
     var wrapper=document.createElement('div');
     wrapper.className='tfng-options';
    getTFNGChoices(question).forEach(function(choice){
      var btn=document.createElement('button');
      btn.type='button';
      btn.className='tfng-option';
       btn.dataset.qid=question.id;
       btn.dataset.choice=choice.value;
       btn.textContent=choice.label;
       if(isChoiceSelected(question,choice.value)){ btn.classList.add('choice-selected'); }
       if(shouldRevealAnswers()){
         if(isCorrectChoice(question,choice.value)){ btn.classList.add('option-correct'); }
         if(isChoiceSelected(question,choice.value) && !isCorrectChoice(question,choice.value)){ btn.classList.add('option-wrong'); }
       }
       if(isInteractionLocked(question)){ btn.disabled=true; }
       btn.addEventListener('click',function(e){ e.preventDefault(); if(isInteractionLocked(question)) return; answers[question.id]=normalizeTFNGValue(choice.value); refreshQuestionCard(question.id); updateProgress(); setCurrentQuestion(question.globalIndex,{scroll:false}); });
       wrapper.appendChild(btn);
     });
     return wrapper;
   }

  function buildAnswerPanel(question){
     var answerText=getAnswerText(question);
     var explanation=question.explanation || question.rationale || '';
     var modelAnswer=question.modelAnswer && question.type==='essay' ? question.modelAnswer : '';
     if(!answerText && !modelAnswer && !explanation) return null;
     if((question.type==='single' || question.type==='multiple' || question.type==='tfng') && !explanation){
       return null;
     }
     if((question.type==='cloze_inline' || question.type==='matching') && !explanation){
       return null;
     }
     var panel=document.createElement('section');
     panel.className='answer-panel';
     panel.dataset.answerPanel='true';
     var html='';
     if(question.type==='essay'){
       if(modelAnswer){ html+='<h4>范文示例</h4><div class="scalable content-reset">'+modelAnswer+'</div>'; }
       if(explanation){ html+='<h4>解析</h4><div class="scalable content-reset">'+explanation+'</div>'; }
     } else if(question.type==='blank'){
       if(answerText){ html+='<h4>参考答案</h4><div class="scalable content-reset">'+answerText+'</div>'; }
       if(explanation){ html+='<h4>解析</h4><div class="scalable content-reset">'+explanation+'</div>'; }
     } else if(question.type==='cloze_inline' || question.type==='matching'){
       if(explanation){ html+='<h4>解析</h4><div class="scalable content-reset">'+explanation+'</div>'; }
     } else if(question.type==='single' || question.type==='multiple' || question.type==='tfng'){
       if(explanation){ html+='<h4>解析</h4><div class="scalable content-reset">'+explanation+'</div>'; }
     } else {
       if(answerText){ html+='<h4>正确答案</h4><div class="scalable content-reset">'+answerText+'</div>'; }
       if(explanation){ html+='<h4>解析</h4><div class="scalable content-reset">'+explanation+'</div>'; }
     }
     panel.innerHTML=html;
     panel.classList.toggle('hidden',!shouldRevealAnswers());
     return panel;
   }
 
  function getAnswerText(question){
    if(!question) return '';
    if(question.type==='tfng'){
      return normalizeTFNGValue(question.answer);
    }
    if(question.type==='cloze_inline'){
      var blanks=Array.isArray(question.blanks)?question.blanks:[];
      if(!blanks.length) return '';
      return blanks.map(function(blank,i){
        var label=blank.label || blank.id || ('Blank '+(i+1));
        return label+': '+(blank.answer||'');
      }).join('<br />');
    }
    if(question.type==='matching'){
      var items=getMatchingItems(question);
      if(!items.length) return '';
      return items.map(function(item){
        return String(item.id)+': '+(item.answer||'');
      }).join('<br />');
    }
    if(question.type==='multiple' || question.type==='single'){
      var opts=getOptions(question);
      var labels=getCorrectLabels(question);
      if(!labels.length) return question.answer||'';
       return labels.map(function(label){
         var found=opts.find(function(opt){ return opt.label===label; });
         return found ? (label+'. '+found.content) : label;
       }).join('<br />');
     }
     if(question.type==='blank' || question.type==='essay'){
       return question.answer || '';
     }
     return question.answer || '';
   }
 
   function updateEssayCounter(container,text,limit){
     if(!container) return;
     var counter=container.querySelector('[data-role="essayCounter"]');
     if(!counter) return;
     var count = text && text.trim() ? text.trim().split(/\s+/).filter(Boolean).length : 0;
     var suffix = limit ? ' / '+limit : '';
     counter.textContent='Words: '+count+suffix;
     if(limit && count>limit){ counter.classList.add('text-rose-600'); }
     else { counter.classList.remove('text-rose-600'); }
   }
 
   function refreshQuestionCard(qId){
     var card=document.getElementById('q-'+qId); if(!card) return;
     var question=questionMap[qId];
     if(question.type==='tfng'){
       card.querySelectorAll('.tfng-option').forEach(function(btn){
         var choice=btn.dataset.choice;
         btn.classList.toggle('choice-selected',isChoiceSelected(question,choice));
         btn.classList.toggle('option-correct',shouldRevealAnswers() && isCorrectChoice(question,choice));
         btn.classList.toggle('option-wrong',shouldRevealAnswers() && isChoiceSelected(question,choice) && !isCorrectChoice(question,choice));
         btn.disabled=isInteractionLocked(question);
       });
     } else if(question.type==='blank' || question.type==='essay'){
       var input=card.querySelector('textarea');
       if(input){
         var correct=getAnswerText(question);
         if(typeof answers[qId]==='string'){ input.value=answers[qId]; }
         input.disabled=isInteractionLocked(question);
         if(shouldRevealAnswers()){
           var wrong = answers[qId] && correct && String(answers[qId]).trim() !== String(correct).trim();
           input.classList.toggle('input-wrong', !!wrong);
         } else {
           input.classList.remove('input-wrong');
         }
       }
       if(question.type==='essay'){ updateEssayCounter(card.querySelector('.space-y-2'), answers[qId], question.wordLimit); }
     } else if(question.type==='cloze_inline'){
       card.querySelectorAll('input.blank-input').forEach(function(input){
         var blankId=input.dataset.blank;
         var map=answers[qId];
         if(map && typeof map==='object' && !Array.isArray(map) && map[blankId]!=null){ input.value=map[blankId]; }
         input.disabled=isInteractionLocked(question);
         input.classList.remove('option-correct','option-wrong');
         if(shouldRevealAnswers()){
           var correct=getCorrectForBlank(question,blankId);
           if(correct && normalizeLabel(input.value)===normalizeLabel(correct)){ input.classList.add('option-correct'); }
           else if(input.value){ input.classList.add('option-wrong'); }
         }
       });
       card.querySelectorAll('[data-blank-solution]').forEach(function(span){
         var blankId=span.getAttribute('data-blank-solution');
         var correct=getCorrectForBlank(question,blankId);
         span.textContent='';
         if(shouldRevealAnswers() && correct){
           var map=answers[qId] || {};
           var userVal=map[blankId] || '';
           var isCorrect=normalizeLabel(userVal)===normalizeLabel(correct);
           if(!isCorrect){ span.textContent='正确答案：'+correct; }
         }
       });
     } else if(question.type==='matching'){
       card.querySelectorAll('.matching-btn').forEach(function(btn){
         var itemId=btn.dataset.itemId;
         var choice=btn.dataset.choice;
         var selected=normalizeLabel(getMatchingAnswer(question,itemId))===normalizeLabel(choice);
         btn.classList.toggle('choice-selected',selected);
         btn.classList.toggle('option-correct',shouldRevealAnswers() && isMatchingCorrect(question,itemId,choice));
         btn.classList.toggle('option-wrong',shouldRevealAnswers() && selected && !isMatchingCorrect(question,itemId,choice));
         btn.disabled=isInteractionLocked(question);
       });
     } else {
       card.querySelectorAll('button.option-btn').forEach(function(btn){
         var choice=btn.dataset.choice;
         btn.classList.toggle('choice-selected',isChoiceSelected(question,choice));
         btn.classList.toggle('option-correct',shouldRevealAnswers() && isCorrectChoice(question,choice));
         btn.classList.toggle('option-wrong',shouldRevealAnswers() && isChoiceSelected(question,choice) && !isCorrectChoice(question,choice));
         btn.disabled=isInteractionLocked(question);
       });
     }
     updateMarkIndicator(qId);
     var panel=card.querySelector('[data-answer-panel="true"]');
     if(panel){ panel.classList.toggle('hidden',!shouldRevealAnswers()); }
   }

   function highlightCard(qId){
     document.querySelectorAll('.question-card.current-question').forEach(function(el){ el.classList.remove('current-question'); });
     var target=document.getElementById('q-'+qId);
     if(target){ target.classList.add('current-question'); }
   }

   function scrollToCard(qId){
     var el=document.getElementById('q-'+qId);
     if(el){ el.scrollIntoView({behavior:'smooth',block:'nearest'}); }
   }

   function setCurrentQuestion(newIndex,opts){
     if(!QuestionStore||!QuestionStore.length) return;
     if(newIndex<0) newIndex=0;
     if(newIndex>=QuestionStore.length) newIndex=QuestionStore.length-1;
     var previous=(idx!=null)?examAPI.getQuestion(idx):null;
     idx=newIndex;
     var current=examAPI.getQuestion(idx);
     if(!current) return;
     if(activePassageId!==current.passageId){ setActivePassage(current.passageId,{force:true}); }
     qBadge.textContent='Q'+current.displayNo;
     sectionLabel.textContent=current.section+' | '+current.passageTitle;
     highlightCard(current.id);
     if(!opts||opts.scroll!==false){ scrollToCard(current.id); }
     if(previous&&previous.passageId!==current.passageId){ showNotice('已进入 '+current.passageTitle,'info'); }
     updateMarkUI();
     updateLockBadge(current);
     updateNavButtons();
   }

  function updateNavButtons(){
     var total=examAPI.getLength();
     if(!total||idx===null){ prevBtn.disabled=true; actionBtn.disabled=true; actionBtn.textContent='Next'; return; }
     prevBtn.disabled = idx<=0;
     if(quizSubmitted){
       actionBtn.disabled = true;
       actionBtn.textContent = '已提交';
       return;
     }
     actionBtn.disabled = false;
     actionBtn.textContent = (idx===total-1)?'Finish':'Next';
   }

   function updateMarkIndicator(qId){
     var card=document.getElementById('q-'+qId);
     if(!card) return;
     var pill=card.querySelector('.mark-pill');
     if(!pill) return;
     pill.classList.toggle('hidden', !marked[qId]);
   }

   function updateLockBadge(question){ lockBadge.classList.toggle('hidden',!(question&&(isLocked(question)||shouldRevealAnswers()))); }

   function updateMarkUI(){
     var current=examAPI.getQuestion(idx);
     if(!current){ markBtn.setAttribute('aria-pressed','false'); markBtn.disabled=true; return; }
     markBtn.disabled=false;
     var state=!!marked[current.id];
     markBtn.setAttribute('aria-pressed',state?'true':'false');
     markBtn.classList.toggle('is-marked',state);
     markIcon.className='w-4 h-4 flag-icon';
     markIcon.classList.add('pop');
     setTimeout(function(){ markIcon.classList.remove('pop'); },220);
     updateMarkIndicator(current.id);
   }

   function answeredCount(){
     var total=0; if(!QuestionStore) return 0;
     for(var i=0;i<QuestionStore.length;i++){ var q=examAPI.getQuestion(i); if(q&&hasAnswer(q.id,q)) total++; }
     return total;
   }

   function updateProgress(){
     var total=examAPI.getLength();
     progressEl.textContent='Answered '+answeredCount()+' / '+total;
     if(navOverlay.classList.contains('visible')) buildNavigator();
   }

   async function submitCurrentSession(){
     if(quizSubmitting){ return; }
     if(!SUBMIT_URL){
       alert('缺少试卷信息，无法提交。');
       return;
     }
     if(!window.confirm('确认提交当前作答？提交后将展示本次得分。')){
       return;
     }

     quizSubmitting = true;
     actionBtn.disabled = true;
     actionBtn.textContent = 'Submitting...';
     try{
       var token = window.localStorage ? window.localStorage.getItem('access_token') : '';
       var headers = { 'Content-Type': 'application/json' };
       if(token){
         headers.Authorization = 'Bearer ' + token;
       }
       var submitBody = {
         answers: answers,
         marked: marked
       };
       if(INLINE_PAYLOAD){
         submitBody.payload = INLINE_PAYLOAD;
       }
       var resp = await fetch(SUBMIT_URL, {
         method: 'POST',
         headers: headers,
         credentials: 'same-origin',
         body: JSON.stringify(submitBody)
       });
       var data = {};
       try { data = await resp.json(); } catch(_e) {}
       if(!resp.ok || !data || data.status !== 'ok'){
         throw new Error((data && data.message) ? data.message : '提交失败，请稍后重试。');
       }
       quizSubmitted = true;
       quizSubmitting = false;
       answersVisible = true;
       if(toggleAnswersBtn){
         toggleAnswersBtn.setAttribute('aria-pressed','true');
         toggleAnswersBtn.textContent='隐藏答案';
       }
       renderActivePassageQuestions();
       if(idx!=null){
         highlightCard(examAPI.getQuestion(idx).id);
       }
       updateLockBadge(examAPI.getQuestion(idx));
       updateNavButtons();
       var result = data.result || {};
       var scoreText = result.score_percent==null ? '暂不支持自动判分' : ('得分 ' + result.score_percent + '%');
       showNotice('已提交，' + scoreText, 'success');
       alert('本次结果：\n答题 ' + (result.answered_count || 0) + ' / ' + (result.total_questions || 0) + '\n答对 ' + (result.correct_count || 0) + ' / ' + (result.gradable_questions || 0) + '\n' + scoreText);
     }catch(err){
       alert(err && err.message ? err.message : '提交失败，请稍后重试。');
       quizSubmitting = false;
       updateNavButtons();
     }
   }

   function statusOf(i){ var q=examAPI.getQuestion(i); if(!q) return 'blank'; if(marked[q.id]) return 'marked'; return hasAnswer(q.id,q)?'answered':'blank'; }

   function buildNavigator(){
     navBody.innerHTML='';
     if(!examData||!examData.passages){ return; }
     examData.passages.forEach(function(passage){
       var wrap=document.createElement('div');
       var title=document.createElement('h3');
       title.className='text-base md:text-lg font-semibold text-slate-700';
       title.textContent=passage.title;
       var grid=document.createElement('div');
       grid.className='mt-2 grid grid-cols-6 sm:grid-cols-9 md:grid-cols-12 gap-2';
       passage.questions.forEach(function(question){
         var i=question.globalIndex;
         var btn=document.createElement('button');
         btn.type='button'; btn.textContent=String(question.displayNo);
         btn.className='qbtn '+statusOf(i)+((i===idx)?' current':'');
         btn.onclick=function(){ setCurrentQuestion(i); hideNavigator(); };
         grid.appendChild(btn);
       });
       wrap.appendChild(title);
       wrap.appendChild(grid);
       navBody.appendChild(wrap);
     });
   }

   function hideNavigator(){ navOverlay.classList.remove('visible'); }
   navBackdrop.addEventListener('click', hideNavigator);
   navClose.addEventListener('click', hideNavigator);
   progressEl.addEventListener('click',function(){ if(!navOverlay.classList.contains('visible')) buildNavigator(); navOverlay.classList.toggle('visible'); });

   markBtn.addEventListener('click',function(){ var current=examAPI.getQuestion(idx); if(!current) return; marked[current.id]=!marked[current.id]; updateMarkUI(); updateProgress(); });
   prevBtn.onclick=function(){ if(examAPI.getLength()===0||idx===null) return; setCurrentQuestion(idx-1); };
   actionBtn.onclick=function(){
     if(examAPI.getLength()===0||idx===null) return;
     if(idx===examAPI.getLength()-1){
       submitCurrentSession();
       return;
     }
     setCurrentQuestion(idx+1);
   };

   document.addEventListener('keydown',function(e){ if(quizPaused) return; if(e.key==='ArrowLeft'||e.key==='ArrowUp'){ prevBtn.click(); } else if(e.key==='ArrowRight'||e.key==='ArrowDown'){ actionBtn.click(); } else if(e.key==='Escape'){ document.exitFullscreen(); } });

   document.addEventListener('dragstart', function(e){ e.preventDefault(); });

   function getPassageById(pid){
     if(!examData||!Array.isArray(examData.passages)) return null;
     return examData.passages.find(function(p){ return p.id===pid; }) || null;
   }

  function renderStimulusPane(passage){
     if(!stimulusPane){ return; }
     if(!passage){
       stimulusModule.textContent=examData?examData.module||'Reading':'Reading';
       stimulusTitle.textContent='请选择题目查看文章';
       stimulusInstructions.textContent='';
       if(questionAudioWrap){
         questionAudioWrap.classList.add('hidden');
         if(questionAudio){ questionAudio.removeAttribute('src'); questionAudio.load(); }
       }
       stimulusContent.innerHTML='<p class="text-slate-500">暂无可显示的阅读材料。</p>';
       updateStimulusLayout(null);
       return;
     }
     var audioUrl = getAudioUrl(passage);
     stimulusModule.textContent=(examData&&examData.module)||'Reading';
     stimulusTitle.textContent=passage.title;
     stimulusInstructions.textContent=passage.instructions||'';
     stimulusInstructions.classList.toggle('hidden',!passage.instructions);
     stimulusContent.innerHTML=passage.content||'<p class="text-slate-500">无阅读材料</p>';
     if(questionAudioWrap){
       if(audioUrl){
         questionAudioWrap.classList.remove('hidden');
         if(questionAudio){ questionAudio.src = audioUrl; }
       } else {
         questionAudioWrap.classList.add('hidden');
         if(questionAudio){ questionAudio.removeAttribute('src'); questionAudio.load(); }
       }
     }
     updateStimulusLayout(passage);
     MathJax.typesetPromise([stimulusPane]).catch(function(err){ console.error(err); });
   }

   function isStimulusEmpty(passage){
     if(!passage) return true;
     if(passage.content == null) return true;
     var text = normalizeText(passage.content);
     if(!text) return true;
     return text === '无阅读材料';
   }

   function updateStimulusLayout(passage){
     var empty = isStimulusEmpty(passage);
     var stage = document.getElementById('stage');
     if(stage){ stage.classList.toggle('no-stimulus', empty); }
     if(questionPane){ questionPane.classList.toggle('centered', empty); }
   }

   renderExam();
   if(examAPI.getLength()>0){ setCurrentQuestion(0,{scroll:false}); }
   startOverlay.style.display='none';
   resumeQuiz();
   updateProgress();
   if(!examData||!examData.passages.length){ renderStimulusPane(null); }

   if(toggleAnswersBtn){
     toggleAnswersBtn.addEventListener('click',function(){
      answersVisible=!answersVisible;
      toggleAnswersBtn.setAttribute('aria-pressed',answersVisible?'true':'false');
      toggleAnswersBtn.textContent=answersVisible?'隐藏答案':'显示答案';
      renderActivePassageQuestions();
      if(idx!=null){ highlightCard(examAPI.getQuestion(idx).id); }
      updateLockBadge(examAPI.getQuestion(idx));
    });
   }

  // —— 水印 ——
  function initWatermark(){ var base=document.getElementById('watermark'); if(!base) return; var text=base.innerText||""; var container=document.createElement('div'); container.style.position='fixed'; container.style.inset='0'; container.style.zIndex='10'; container.style.pointerEvents='none'; container.style.userSelect='none'; var rows=6, cols=6; for(var y=0;y<rows;y++){ for(var x=0;x<cols;x++){ var wm=document.createElement('div'); wm.className='watermark-tile'; wm.innerText=text; wm.style.top=(y*20)+'%'; wm.style.left=(x*20)+'%'; container.appendChild(wm); } } document.body.appendChild(container); }
  if(document.readyState==='loading'){ document.addEventListener('DOMContentLoaded', initWatermark); } else { initWatermark(); }

  // —— 禁止右键与常见快捷键 ——
  window.addEventListener('contextmenu',function(e){e.preventDefault();});
  document.addEventListener('keydown',function(e){ var isCtrlShift=e.ctrlKey && e.shiftKey; if(e.key==='F12'||e.keyCode===123||(isCtrlShift && e.key.toLowerCase()==='i')||(isCtrlShift && e.key.toLowerCase()==='j')||(e.ctrlKey && e.key.toLowerCase()==='u')||(e.ctrlKey && e.key.toLowerCase()==='c')){ e.preventDefault(); debugger; return false; }});

  function showNotice(message, type = 'info') {
      var n = document.createElement('div');
      n.className = 'toast toast-enter toast-' + type;
      n.setAttribute('role', 'status');
      n.setAttribute('aria-live', 'polite');
      n.textContent = message;

      document.body.appendChild(n);

      setTimeout(() => {
          n.classList.remove('toast-enter');
          n.classList.add('toast-leave');
      }, 1400);

      setTimeout(() => {
          n.remove();
      }, 2100);
  }

  window.pageLoadTime = Math.floor(Date.now() / 1000);
