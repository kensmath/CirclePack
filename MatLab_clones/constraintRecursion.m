function [C,rad,newU] = constraintRecursion(uzians)
% [C,rad,newU] = constraintRecursion(uzians)
%   Look at intrinsic schwarzian computations,
%   particularly the "constraints" for branched
%   flowers. Argument is uzians {u1,u2,...u_{n-1}},
%   u=1-s.
sq3=sqrt(3);
sz=size(uzians);
n=sz(2);
C(1)=1.0;
rad(1)=1;
C(2)=sq3*uzians(1);
rad(2)=1/(C(2)*C(2));
rad(n-1)=1.0;
for j=3:n-2
    if C(j-2)<0
        C(j)=-1*sq3*uzians(j-1)*C(j-1)-C(j-2);
    else
        C(j)=sq3*uzians(j-1)*C(j-1)-C(j-2);
    end
    rad(j)=1/(C(j)*C(j));
end
newU=(1.0+C(n-3))/(sq3*C(n-2));
end